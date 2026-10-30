package com.lendbridge.loan.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lendbridge.loan.dto.LoanReviewDecisionRequest;
import com.lendbridge.loan.dto.LoanReviewQueueItemResponse;
import com.lendbridge.loan.dto.LoanResponse;
import com.lendbridge.loan.mapper.LoanMapper;
import com.lendbridge.loan.model.LoanApplication;
import com.lendbridge.loan.model.LoanPurpose;
import com.lendbridge.loan.model.LoanReviewDecision;
import com.lendbridge.loan.model.LoanStatus;
import com.lendbridge.loan.repository.LoanApplicationRepository;
import com.lendbridge.starter.exception.BadRequestException;
import com.lendbridge.starter.exception.ConflictException;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoanReviewServiceImplTest {

  private LoanApplicationRepository repository;
  private OutboxEventWriter outboxEventWriter;
  private LoanReviewServiceImpl service;

  @BeforeEach
  void setUp() {
    repository = mock(LoanApplicationRepository.class);
    outboxEventWriter = mock(OutboxEventWriter.class);
    service = new LoanReviewServiceImpl(repository, outboxEventWriter, new LoanMapper(), 14);
  }

  private LoanApplication underReviewLoan() {
    Instant now = Instant.now();
    return LoanApplication.builder()
        .id(UUID.randomUUID())
        .borrowerUserId(UUID.randomUUID())
        .amountKobo(1_000_000L)
        .tenorMonths(12)
        .purpose(LoanPurpose.BUSINESS)
        .interestRateBps(1800)
        .status(LoanStatus.UNDER_REVIEW)
        .reviewRequired(true)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  @Test
  void getQueue_mapsEveryUnderReviewLoan() {
    LoanApplication loan = underReviewLoan();
    when(repository.findAllByStatusOrderByCreatedAtAsc(LoanStatus.UNDER_REVIEW)).thenReturn(List.of(loan));

    List<LoanReviewQueueItemResponse> queue = service.getQueue();

    assertThat(queue).hasSize(1);
    assertThat(queue.get(0).getId()).isEqualTo(loan.getId());
  }

  @Test
  void decide_approve_movesToListedAndPublishesEvent() {
    LoanApplication loan = underReviewLoan();
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));
    UUID officerId = UUID.randomUUID();

    LoanResponse response =
        service.decide(loan.getId(), new LoanReviewDecisionRequest(LoanReviewDecision.APPROVE, null), officerId);

    assertThat(response.getStatus()).isEqualTo(LoanStatus.LISTED);
    assertThat(response.getListedAt()).isNotNull();
    assertThat(response.getExpiresAt()).isNotNull();
    verify(outboxEventWriter).write(any(), any(), any(), any());
  }

  @Test
  void decide_rejectWithoutReason_throwsBadRequest() {
    LoanApplication loan = underReviewLoan();
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    assertThatThrownBy(
            () ->
                service.decide(
                    loan.getId(), new LoanReviewDecisionRequest(LoanReviewDecision.REJECT, null), UUID.randomUUID()))
        .isInstanceOf(BadRequestException.class);
    verifyNoInteractions(outboxEventWriter);
  }

  @Test
  void decide_rejectWithReason_setsRejectedAndReason() {
    LoanApplication loan = underReviewLoan();
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    LoanResponse response =
        service.decide(
            loan.getId(),
            new LoanReviewDecisionRequest(LoanReviewDecision.REJECT, "insufficient income evidence"),
            UUID.randomUUID());

    assertThat(response.getStatus()).isEqualTo(LoanStatus.REJECTED);
    assertThat(response.getRejectionReason()).isEqualTo("insufficient income evidence");
  }

  @Test
  void decide_alreadyDecided_throwsConflict() {
    LoanApplication loan = underReviewLoan();
    loan.setStatus(LoanStatus.LISTED);
    when(repository.findById(loan.getId())).thenReturn(Optional.of(loan));

    assertThatThrownBy(
            () ->
                service.decide(
                    loan.getId(), new LoanReviewDecisionRequest(LoanReviewDecision.APPROVE, null), UUID.randomUUID()))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void decide_unknownLoan_throwsNotFound() {
    UUID loanId = UUID.randomUUID();
    when(repository.findById(loanId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.decide(loanId, new LoanReviewDecisionRequest(LoanReviewDecision.APPROVE, null), UUID.randomUUID()))
        .isInstanceOf(NotFoundException.class);
  }
}
