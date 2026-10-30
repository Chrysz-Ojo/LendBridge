package com.lendbridge.investment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.dto.CommitmentRequest;
import com.lendbridge.investment.dto.CommitmentResponse;
import com.lendbridge.investment.model.Commitment;
import com.lendbridge.investment.model.CommitmentStatus;
import com.lendbridge.investment.model.LoanListing;
import com.lendbridge.investment.repository.CommitmentRepository;
import com.lendbridge.investment.repository.LoanListingRepository;
import com.lendbridge.investment.wallet.WalletGateway;
import com.lendbridge.starter.exception.BadRequestException;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnprocessableEntityException;
import com.lendbridge.starter.idempotency.IdempotencyOperations;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommitmentServiceImplTest {

  private static final long MIN_TICKET_KOBO = 500_000L;
  private static final double MAX_EXPOSURE_RATIO = 0.20;

  @Mock private CommitmentRepository commitmentRepository;
  @Mock private LoanListingRepository loanListingRepository;
  @Mock private WalletGateway walletGateway;
  @Mock private OutboxEventWriter outboxEventWriter;

  private CommitmentServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new CommitmentServiceImpl(
            commitmentRepository,
            loanListingRepository,
            walletGateway,
            outboxEventWriter,
            new IdempotencyOperations(),
            MIN_TICKET_KOBO,
            MAX_EXPOSURE_RATIO);
  }

  private LoanListing activeListing(UUID loanId, UUID borrowerUserId, long amountKobo) {
    return LoanListing.builder().id(loanId).borrowerUserId(borrowerUserId).amountKobo(amountKobo).active(true).build();
  }

  @Test
  void create_withinLimits_createsAHoldAndACommitment() {
    UUID lenderId = UUID.randomUUID();
    UUID loanId = UUID.randomUUID();
    UUID holdId = UUID.randomUUID();
    when(commitmentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
    when(loanListingRepository.findById(loanId))
        .thenReturn(Optional.of(activeListing(loanId, UUID.randomUUID(), 1_000_000L)));
    when(commitmentRepository.findAllByLoanIdAndStatus(loanId, CommitmentStatus.ACTIVE)).thenReturn(List.of());
    when(commitmentRepository.findAllByLenderUserIdAndStatus(lenderId, CommitmentStatus.ACTIVE)).thenReturn(List.of());
    when(walletGateway.createHold(anyString(), anyLong(), anyString())).thenReturn(holdId);

    CommitmentResponse response = service.create(lenderId, loanId, new CommitmentRequest(600_000L), "key-1");

    assertThat(response.getLoanId()).isEqualTo(loanId);
    assertThat(response.getStatus()).isEqualTo(CommitmentStatus.ACTIVE);
    verify(walletGateway).createHold("key-1", 600_000L, "loan:" + loanId);
    ArgumentCaptor<Commitment> captor = ArgumentCaptor.forClass(Commitment.class);
    verify(commitmentRepository).save(captor.capture());
    assertThat(captor.getValue().getHoldId()).isEqualTo(holdId);
    verify(outboxEventWriter).write(any(), any(), any(), any());
  }

  @Test
  void create_belowMinimumTicket_throwsBadRequest() {
    UUID lenderId = UUID.randomUUID();
    UUID loanId = UUID.randomUUID();
    when(commitmentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(lenderId, loanId, new CommitmentRequest(100_000L), "key-2"))
        .isInstanceOf(BadRequestException.class);
    verify(walletGateway, never()).createHold(any(), anyLong(), any());
  }

  @Test
  void create_noActiveListingForLoan_throwsNotFound() {
    UUID lenderId = UUID.randomUUID();
    UUID loanId = UUID.randomUUID();
    when(commitmentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
    when(loanListingRepository.findById(loanId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(lenderId, loanId, new CommitmentRequest(600_000L), "key-3"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void create_exceedsRemainingUnfundedAmount_throwsUnprocessableEntity() {
    UUID lenderId = UUID.randomUUID();
    UUID loanId = UUID.randomUUID();
    when(commitmentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
    when(loanListingRepository.findById(loanId))
        .thenReturn(Optional.of(activeListing(loanId, UUID.randomUUID(), 1_000_000L)));
    Commitment existing =
        Commitment.builder().loanId(loanId).amountKobo(900_000L).status(CommitmentStatus.ACTIVE).build();
    when(commitmentRepository.findAllByLoanIdAndStatus(loanId, CommitmentStatus.ACTIVE)).thenReturn(List.of(existing));

    assertThatThrownBy(() -> service.create(lenderId, loanId, new CommitmentRequest(600_000L), "key-4"))
        .isInstanceOf(UnprocessableEntityException.class);
    verify(walletGateway, never()).createHold(any(), anyLong(), any());
  }

  @Test
  void create_firstEverCommitment_skipsTheBorrowerExposureCap() {
    UUID lenderId = UUID.randomUUID();
    UUID loanId = UUID.randomUUID();
    when(commitmentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
    when(loanListingRepository.findById(loanId))
        .thenReturn(Optional.of(activeListing(loanId, UUID.randomUUID(), 5_000_000L)));
    when(commitmentRepository.findAllByLoanIdAndStatus(loanId, CommitmentStatus.ACTIVE)).thenReturn(List.of());
    when(commitmentRepository.findAllByLenderUserIdAndStatus(lenderId, CommitmentStatus.ACTIVE)).thenReturn(List.of());
    when(walletGateway.createHold(any(), anyLong(), any())).thenReturn(UUID.randomUUID());

    // 100% of this lender's (so far empty) portfolio, would fail the 20% cap for a second
    // commitment — but a lender's very first commitment is unavoidably 100% of nothing yet.
    CommitmentResponse response = service.create(lenderId, loanId, new CommitmentRequest(2_000_000L), "key-5");

    assertThat(response.getStatus()).isEqualTo(CommitmentStatus.ACTIVE);
  }

  @Test
  void create_secondCommitmentToSameBorrowerExceedsExposureCap_throwsUnprocessableEntity() {
    UUID lenderId = UUID.randomUUID();
    UUID borrowerId = UUID.randomUUID();
    UUID existingLoanId = UUID.randomUUID();
    UUID newLoanId = UUID.randomUUID();

    when(commitmentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
    when(loanListingRepository.findById(newLoanId))
        .thenReturn(Optional.of(activeListing(newLoanId, borrowerId, 5_000_000L)));
    when(loanListingRepository.findById(existingLoanId))
        .thenReturn(Optional.of(activeListing(existingLoanId, borrowerId, 5_000_000L)));
    when(commitmentRepository.findAllByLoanIdAndStatus(newLoanId, CommitmentStatus.ACTIVE)).thenReturn(List.of());
    Commitment existingCommitment =
        Commitment.builder()
            .loanId(existingLoanId)
            .amountKobo(1_000_000L)
            .status(CommitmentStatus.ACTIVE)
            .build();
    when(commitmentRepository.findAllByLenderUserIdAndStatus(lenderId, CommitmentStatus.ACTIVE))
        .thenReturn(List.of(existingCommitment));

    // Both commitments go to the same borrower -> 100% concentration, well above the 20% cap.
    assertThatThrownBy(() -> service.create(lenderId, newLoanId, new CommitmentRequest(1_000_000L), "key-6"))
        .isInstanceOf(UnprocessableEntityException.class);
    verify(walletGateway, never()).createHold(any(), anyLong(), any());
  }

  @Test
  void create_duplicateIdempotencyKey_returnsExistingWithoutCallingWalletAgain() {
    UUID lenderId = UUID.randomUUID();
    UUID loanId = UUID.randomUUID();
    Commitment existing =
        Commitment.builder()
            .id(UUID.randomUUID())
            .loanId(loanId)
            .amountKobo(600_000L)
            .status(CommitmentStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();
    when(commitmentRepository.findByIdempotencyKey("key-7")).thenReturn(Optional.of(existing));

    CommitmentResponse response = service.create(lenderId, loanId, new CommitmentRequest(600_000L), "key-7");

    assertThat(response.getId()).isEqualTo(existing.getId());
    verify(walletGateway, never()).createHold(any(), anyLong(), any());
    verify(commitmentRepository, never()).save(any());
  }

  @Test
  void cancel_activeCommitment_releasesTheHoldAndPublishesEvent() {
    UUID lenderId = UUID.randomUUID();
    UUID holdId = UUID.randomUUID();
    Commitment commitment =
        Commitment.builder()
            .id(UUID.randomUUID())
            .lenderUserId(lenderId)
            .loanId(UUID.randomUUID())
            .amountKobo(600_000L)
            .holdId(holdId)
            .status(CommitmentStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();
    when(commitmentRepository.findById(commitment.getId())).thenReturn(Optional.of(commitment));

    CommitmentResponse response = service.cancel(lenderId, commitment.getId());

    assertThat(response.getStatus()).isEqualTo(CommitmentStatus.CANCELLED);
    verify(walletGateway).releaseHold(holdId);
    verify(outboxEventWriter).write(any(), any(), any(), any());
  }

  @Test
  void cancel_alreadyCancelled_isIdempotentNoOp() {
    UUID lenderId = UUID.randomUUID();
    Commitment commitment =
        Commitment.builder()
            .id(UUID.randomUUID())
            .lenderUserId(lenderId)
            .status(CommitmentStatus.CANCELLED)
            .cancelledAt(Instant.now())
            .build();
    when(commitmentRepository.findById(commitment.getId())).thenReturn(Optional.of(commitment));

    service.cancel(lenderId, commitment.getId());

    verify(walletGateway, never()).releaseHold(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  @Test
  void cancel_notOwnedByCaller_throwsNotFound() {
    UUID ownerId = UUID.randomUUID();
    UUID otherLenderId = UUID.randomUUID();
    Commitment commitment =
        Commitment.builder().id(UUID.randomUUID()).lenderUserId(ownerId).status(CommitmentStatus.ACTIVE).build();
    when(commitmentRepository.findById(commitment.getId())).thenReturn(Optional.of(commitment));

    assertThatThrownBy(() -> service.cancel(otherLenderId, commitment.getId())).isInstanceOf(NotFoundException.class);
  }
}
