package com.lendbridge.loan.service.impl;

import com.lendbridge.loan.dto.LoanResponse;
import com.lendbridge.loan.dto.LoanReviewDecisionRequest;
import com.lendbridge.loan.dto.LoanReviewQueueItemResponse;
import com.lendbridge.loan.dto.ScheduleResponse;
import com.lendbridge.loan.mapper.LoanMapper;
import com.lendbridge.loan.model.LoanApplication;
import com.lendbridge.loan.model.LoanStatus;
import com.lendbridge.loan.outbox.KafkaTopics;
import com.lendbridge.loan.outbox.LoanListedPayload;
import com.lendbridge.loan.repository.LoanApplicationRepository;
import com.lendbridge.loan.service.LoanReviewService;
import com.lendbridge.starter.exception.BadRequestException;
import com.lendbridge.starter.exception.ConflictException;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mirrors kyc-service's {@code ReviewServiceImpl} exactly: a {@code RISK_OFFICER} stands in for
 * the not-yet-built Risk service (see docs/adr/0015-loan-service-phase-1-scope.md). */
@Service
public class LoanReviewServiceImpl implements LoanReviewService {

  private final LoanApplicationRepository loanApplicationRepository;
  private final OutboxEventWriter outboxEventWriter;
  private final LoanMapper loanMapper;
  private final int listingExpiryDays;

  public LoanReviewServiceImpl(
      LoanApplicationRepository loanApplicationRepository,
      OutboxEventWriter outboxEventWriter,
      LoanMapper loanMapper,
      @Value("${loan.listing.expiry-days}") int listingExpiryDays) {
    this.loanApplicationRepository = loanApplicationRepository;
    this.outboxEventWriter = outboxEventWriter;
    this.loanMapper = loanMapper;
    this.listingExpiryDays = listingExpiryDays;
  }

  @Override
  @Transactional(readOnly = true)
  public List<LoanReviewQueueItemResponse> getQueue() {
    return loanApplicationRepository.findAllByStatusOrderByCreatedAtAsc(LoanStatus.UNDER_REVIEW).stream()
        .map(loanMapper::toQueueItem)
        .toList();
  }

  @Override
  @Transactional
  public LoanResponse decide(UUID loanId, LoanReviewDecisionRequest request, UUID officerUserId) {
    LoanApplication loan =
        loanApplicationRepository
            .findById(loanId)
            .orElseThrow(() -> new NotFoundException("LOAN_NOT_FOUND", "Loan request not found"));
    if (loan.getStatus() != LoanStatus.UNDER_REVIEW) {
      throw new ConflictException("LOAN_ALREADY_DECIDED", "This loan request has already been decided");
    }

    Instant now = Instant.now();
    switch (request.getDecision()) {
      case APPROVE -> approve(loan, now);
      case REJECT -> reject(loan, request, now);
    }
    loan.setDecidedByUserId(officerUserId);
    loan.setDecidedAt(now);
    loan.setUpdatedAt(now);
    loan.setReviewRequired(false);
    loanApplicationRepository.save(loan);

    return loanMapper.toResponse(loan, scheduleFor(loan));
  }

  private void approve(LoanApplication loan, Instant now) {
    loan.setStatus(LoanStatus.LISTED);
    loan.setListedAt(now);
    loan.setExpiresAt(now.plus(listingExpiryDays, ChronoUnit.DAYS));
    loan.setRejectionReason(null);

    outboxEventWriter.write(
        "LoanApplication",
        loan.getId().toString(),
        KafkaTopics.LOAN_LISTED,
        LoanListedPayload.builder()
            .loanId(loan.getId())
            .borrowerUserId(loan.getBorrowerUserId())
            .amountKobo(loan.getAmountKobo())
            .interestRateBps(loan.getInterestRateBps())
            .tenorMonths(loan.getTenorMonths())
            .occurredAtEpochMillis(now.toEpochMilli())
            .build());
  }

  private void reject(LoanApplication loan, LoanReviewDecisionRequest request, Instant now) {
    if (request.getReason() == null || request.getReason().isBlank()) {
      throw new BadRequestException("REVIEW_REASON_REQUIRED", "A reason is required to reject a loan request");
    }
    loan.setStatus(LoanStatus.REJECTED);
    loan.setRejectionReason(request.getReason());
  }

  private ScheduleResponse scheduleFor(LoanApplication loan) {
    return RepaymentScheduleCalculator.compute(
        ScheduleCalculationRequest.builder()
            .amountKobo(loan.getAmountKobo())
            .tenorMonths(loan.getTenorMonths())
            .interestRateBps(loan.getInterestRateBps())
            .startDate(loan.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate())
            .build());
  }
}
