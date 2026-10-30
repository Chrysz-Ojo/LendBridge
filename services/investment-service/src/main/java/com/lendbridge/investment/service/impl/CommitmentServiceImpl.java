package com.lendbridge.investment.service.impl;

import com.lendbridge.investment.dto.CommitmentRequest;
import com.lendbridge.investment.dto.CommitmentResponse;
import com.lendbridge.investment.model.Commitment;
import com.lendbridge.investment.model.CommitmentStatus;
import com.lendbridge.investment.model.LoanListing;
import com.lendbridge.investment.outbox.InvestmentCancelledPayload;
import com.lendbridge.investment.outbox.InvestmentCommittedPayload;
import com.lendbridge.investment.outbox.KafkaTopics;
import com.lendbridge.investment.repository.CommitmentRepository;
import com.lendbridge.investment.repository.LoanListingRepository;
import com.lendbridge.investment.service.CommitmentService;
import com.lendbridge.investment.wallet.WalletGateway;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.BadRequestException;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnprocessableEntityException;
import com.lendbridge.starter.idempotency.IdempotencyOperations;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The borrower-exposure cap (PRD E3-S3) and the minimum-ticket/remaining-unfunded checks (E4-S1)
 * all run entirely off this service's own {@link LoanListing} projection and {@link Commitment}
 * table — no synchronous call to loan-service or marketplace-service (docs/adr/0017).
 */
@Service
public class CommitmentServiceImpl implements CommitmentService {

  private final CommitmentRepository commitmentRepository;
  private final LoanListingRepository loanListingRepository;
  private final WalletGateway walletGateway;
  private final OutboxEventWriter outboxEventWriter;
  private final IdempotencyOperations idempotencyOperations;
  private final long minTicketKobo;
  private final double maxBorrowerExposureRatio;

  public CommitmentServiceImpl(
      CommitmentRepository commitmentRepository,
      LoanListingRepository loanListingRepository,
      WalletGateway walletGateway,
      OutboxEventWriter outboxEventWriter,
      IdempotencyOperations idempotencyOperations,
      @Value("${investment.min-ticket-kobo}") long minTicketKobo,
      @Value("${investment.max-borrower-exposure-ratio}") double maxBorrowerExposureRatio) {
    this.commitmentRepository = commitmentRepository;
    this.loanListingRepository = loanListingRepository;
    this.walletGateway = walletGateway;
    this.outboxEventWriter = outboxEventWriter;
    this.idempotencyOperations = idempotencyOperations;
    this.minTicketKobo = minTicketKobo;
    this.maxBorrowerExposureRatio = maxBorrowerExposureRatio;
  }

  @Override
  @Transactional
  public CommitmentResponse create(UUID lenderUserId, UUID loanId, CommitmentRequest request, String idempotencyKey) {
    return idempotencyOperations.runIdempotently(
        () -> commitmentRepository.findByIdempotencyKey(idempotencyKey).map(this::toResponse),
        () -> createCommitmentRecord(lenderUserId, loanId, request, idempotencyKey));
  }

  private CommitmentResponse createCommitmentRecord(
      UUID lenderUserId, UUID loanId, CommitmentRequest request, String idempotencyKey) {
    long amountKobo = request.getAmountKobo();
    if (amountKobo < minTicketKobo) {
      throw new BadRequestException(
          "COMMITMENT_BELOW_MINIMUM_TICKET", "Minimum commitment is " + minTicketKobo + " kobo");
    }

    LoanListing loanListing =
        loanListingRepository
            .findById(loanId)
            .filter(LoanListing::isActive)
            .orElseThrow(() -> new NotFoundException("LOAN_LISTING_NOT_FOUND", "No active listing for this loan"));

    long alreadyFunded =
        commitmentRepository.findAllByLoanIdAndStatus(loanId, CommitmentStatus.ACTIVE).stream()
            .mapToLong(Commitment::getAmountKobo)
            .sum();
    long remainingUnfunded = loanListing.getAmountKobo() - alreadyFunded;
    if (amountKobo > remainingUnfunded) {
      throw new UnprocessableEntityException(
          "COMMITMENT_EXCEEDS_REMAINING_UNFUNDED",
          "Amount exceeds the loan's remaining unfunded amount (" + remainingUnfunded + " kobo)");
    }

    enforceBorrowerExposureCap(lenderUserId, loanListing.getBorrowerUserId(), amountKobo);

    UUID holdId = walletGateway.createHold(idempotencyKey, amountKobo, "loan:" + loanId);

    Instant now = Instant.now();
    Commitment commitment =
        Commitment.builder()
            .id(UuidV7.generate())
            .lenderUserId(lenderUserId)
            .loanId(loanId)
            .amountKobo(amountKobo)
            .holdId(holdId)
            .status(CommitmentStatus.ACTIVE)
            .idempotencyKey(idempotencyKey)
            .createdAt(now)
            .build();
    commitmentRepository.save(commitment);

    outboxEventWriter.write(
        "Commitment",
        commitment.getId().toString(),
        KafkaTopics.INVESTMENT_COMMITTED,
        InvestmentCommittedPayload.builder()
            .commitmentId(commitment.getId())
            .loanId(loanId)
            .lenderUserId(lenderUserId)
            .amountKobo(amountKobo)
            .occurredAtEpochMillis(now.toEpochMilli())
            .build());

    return toResponse(commitment);
  }

  /** A lender's very first active commitment is skipped — it's unavoidably 100% of a zero-item
   * portfolio, and the rule's intent is discouraging concentration once there's a portfolio to
   * compare against, not blocking anyone's first investment (docs/adr/0017). */
  private void enforceBorrowerExposureCap(UUID lenderUserId, UUID borrowerUserId, long newAmountKobo) {
    List<Commitment> activeCommitments =
        commitmentRepository.findAllByLenderUserIdAndStatus(lenderUserId, CommitmentStatus.ACTIVE);
    if (activeCommitments.isEmpty()) {
      return;
    }
    long totalExisting = activeCommitments.stream().mapToLong(Commitment::getAmountKobo).sum();
    long existingToThisBorrower =
        activeCommitments.stream()
            .filter(c -> borrowerUserId.equals(borrowerOf(c.getLoanId())))
            .mapToLong(Commitment::getAmountKobo)
            .sum();
    long newTotal = totalExisting + newAmountKobo;
    long newToThisBorrower = existingToThisBorrower + newAmountKobo;
    if ((double) newToThisBorrower / newTotal > maxBorrowerExposureRatio) {
      throw new UnprocessableEntityException(
          "BORROWER_EXPOSURE_CAP_EXCEEDED",
          "This commitment would exceed your maximum exposure to a single borrower ("
              + Math.round(maxBorrowerExposureRatio * 100)
              + "%)");
    }
  }

  private UUID borrowerOf(UUID loanId) {
    return loanListingRepository.findById(loanId).map(LoanListing::getBorrowerUserId).orElse(null);
  }

  @Override
  @Transactional
  public CommitmentResponse cancel(UUID lenderUserId, UUID commitmentId) {
    Commitment commitment =
        commitmentRepository
            .findById(commitmentId)
            .filter(c -> c.getLenderUserId().equals(lenderUserId))
            .orElseThrow(() -> new NotFoundException("COMMITMENT_NOT_FOUND", "Commitment not found"));

    if (commitment.getStatus() == CommitmentStatus.CANCELLED) {
      return toResponse(commitment);
    }

    walletGateway.releaseHold(commitment.getHoldId());

    Instant now = Instant.now();
    commitment.setStatus(CommitmentStatus.CANCELLED);
    commitment.setCancelledAt(now);
    commitmentRepository.save(commitment);

    outboxEventWriter.write(
        "Commitment",
        commitment.getId().toString(),
        KafkaTopics.INVESTMENT_CANCELLED,
        InvestmentCancelledPayload.builder()
            .commitmentId(commitment.getId())
            .loanId(commitment.getLoanId())
            .amountKobo(commitment.getAmountKobo())
            .occurredAtEpochMillis(now.toEpochMilli())
            .build());

    return toResponse(commitment);
  }

  @Override
  @Transactional(readOnly = true)
  public List<CommitmentResponse> listMine(UUID lenderUserId) {
    return commitmentRepository.findAllByLenderUserIdOrderByCreatedAtDesc(lenderUserId).stream()
        .map(this::toResponse)
        .toList();
  }

  private CommitmentResponse toResponse(Commitment commitment) {
    return CommitmentResponse.builder()
        .id(commitment.getId())
        .loanId(commitment.getLoanId())
        .amountKobo(commitment.getAmountKobo())
        .status(commitment.getStatus())
        .createdAt(commitment.getCreatedAt())
        .cancelledAt(commitment.getCancelledAt())
        .build();
  }
}
