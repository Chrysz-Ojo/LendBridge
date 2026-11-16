package com.lendbridge.investment.fundingsaga;

import com.lendbridge.investment.model.Commitment;
import com.lendbridge.investment.model.CommitmentStatus;
import com.lendbridge.investment.model.DisbursementAssembly;
import com.lendbridge.investment.outbox.DisbursementLegPayload;
import com.lendbridge.investment.outbox.InvestmentDisbursementCompensatePayload;
import com.lendbridge.investment.outbox.InvestmentDisbursementReadyPayload;
import com.lendbridge.investment.outbox.InvestmentHoldsCaptureRequestedPayload;
import com.lendbridge.investment.outbox.KafkaTopics;
import com.lendbridge.investment.repository.CommitmentRepository;
import com.lendbridge.investment.repository.DisbursementAssemblyRepository;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The funding saga's aggregator (full design: docs the saga's event choreography — see the
 * funding-saga plan). Three legs feed in independently — {@link #onAgreementSigned},
 * {@link #onHoldsCaptureAssembled}, {@link #onBorrowerAccountAssembled} — each an upsert into the
 * {@link DisbursementAssembly} row for that loan; whichever call finds the other two legs already
 * present acts: either {@code investment.disbursement.ready} (every leg succeeded) or
 * {@code investment.disbursement.compensate} (any leg failed), then deletes the row. Called from
 * plain (non-{@code @Transactional}) Kafka consumers, so each method here owns its own transaction
 * boundary — same shape as every other consumer in this build.
 */
@Component
public class DisbursementAssembler {

  private final DisbursementAssemblyRepository assemblyRepository;
  private final CommitmentRepository commitmentRepository;
  private final OutboxEventWriter outboxEventWriter;

  public DisbursementAssembler(
      DisbursementAssemblyRepository assemblyRepository,
      CommitmentRepository commitmentRepository,
      OutboxEventWriter outboxEventWriter) {
    this.assemblyRepository = assemblyRepository;
    this.commitmentRepository = commitmentRepository;
    this.outboxEventWriter = outboxEventWriter;
  }

  @Transactional
  public void onAgreementSigned(UUID loanId, long originationFeeKobo) {
    DisbursementAssembly assembly = findOrCreate(loanId);
    assembly.setSignedReceived(true);
    assembly.setOriginationFeeKobo(originationFeeKobo);
    assemblyRepository.save(assembly);

    List<Commitment> activeCommitments =
        commitmentRepository.findAllByLoanIdAndStatus(loanId, CommitmentStatus.ACTIVE);
    List<String> holdIds = activeCommitments.stream().map(c -> c.getHoldId().toString()).toList();
    long totalKobo = activeCommitments.stream().mapToLong(Commitment::getAmountKobo).sum();

    outboxEventWriter.write(
        "Loan",
        loanId.toString(),
        KafkaTopics.INVESTMENT_HOLDS_CAPTURE_REQUESTED,
        new InvestmentHoldsCaptureRequestedPayload(
            loanId.toString(), holdIds, totalKobo, Instant.now().toEpochMilli()));

    tryComplete(assembly);
  }

  @Transactional
  public void onHoldsCaptureAssembled(UUID loanId, boolean success, String failureReason) {
    DisbursementAssembly assembly = findOrCreate(loanId);
    assembly.setHoldsSuccess(success);
    assembly.setHoldsFailureReason(failureReason);
    assemblyRepository.save(assembly);
    tryComplete(assembly);
  }

  @Transactional
  public void onBorrowerAccountAssembled(
      UUID loanId, boolean success, UUID borrowerLedgerAccountId, String failureReason) {
    DisbursementAssembly assembly = findOrCreate(loanId);
    assembly.setBorrowerAccountSuccess(success);
    assembly.setBorrowerLedgerAccountId(borrowerLedgerAccountId);
    assembly.setBorrowerAccountFailureReason(failureReason);
    assemblyRepository.save(assembly);
    tryComplete(assembly);
  }

  /** Ledger-posting itself failed, <i>after</i> {@link #tryComplete} already published
   * {@code investment.disbursement.ready} and deleted the assembly row for this loan — there is no
   * row left to join against, just the one compensating action the capture already needs undone. */
  @Transactional
  public void onLedgerDisbursementFailed(UUID loanId) {
    publishCompensate(loanId);
  }

  private void tryComplete(DisbursementAssembly assembly) {
    if (!assembly.isComplete()) {
      return;
    }
    UUID loanId = assembly.getLoanId();
    if (assembly.isFullySuccessful()) {
      publishReady(assembly);
    } else {
      publishCompensate(loanId);
    }
    assemblyRepository.delete(assembly);
  }

  private void publishReady(DisbursementAssembly assembly) {
    UUID loanId = assembly.getLoanId();
    List<Commitment> activeCommitments =
        commitmentRepository.findAllByLoanIdAndStatus(loanId, CommitmentStatus.ACTIVE);
    List<DisbursementLegPayload> legs =
        activeCommitments.stream()
            .map(
                c ->
                    new DisbursementLegPayload(
                        c.getLenderLedgerAccountId().toString(), c.getAmountKobo()))
            .toList();
    long totalKobo = activeCommitments.stream().mapToLong(Commitment::getAmountKobo).sum();

    outboxEventWriter.write(
        "Loan",
        loanId.toString(),
        KafkaTopics.INVESTMENT_DISBURSEMENT_READY,
        new InvestmentDisbursementReadyPayload(
            loanId.toString(),
            assembly.getBorrowerLedgerAccountId().toString(),
            assembly.getOriginationFeeKobo(),
            totalKobo,
            legs,
            Instant.now().toEpochMilli()));
  }

  private void publishCompensate(UUID loanId) {
    List<String> holdIds =
        commitmentRepository.findAllByLoanIdAndStatus(loanId, CommitmentStatus.ACTIVE).stream()
            .map(c -> c.getHoldId().toString())
            .toList();

    outboxEventWriter.write(
        "Loan",
        loanId.toString(),
        KafkaTopics.INVESTMENT_DISBURSEMENT_COMPENSATE,
        new InvestmentDisbursementCompensatePayload(
            loanId.toString(), holdIds, Instant.now().toEpochMilli()));
  }

  /** Same race-handling idiom as {@code WalletServiceImpl.createWalletRecord}: two legs can race
   * to create the same row (they're triggered by independent upstream events, not a single causal
   * chain), so a lost insert race is resolved by re-reading rather than erroring. */
  private DisbursementAssembly findOrCreate(UUID loanId) {
    return assemblyRepository
        .findById(loanId)
        .orElseGet(
            () -> {
              try {
                return assemblyRepository.save(
                    DisbursementAssembly.builder().loanId(loanId).createdAt(Instant.now()).build());
              } catch (DataIntegrityViolationException e) {
                return assemblyRepository.findById(loanId).orElseThrow(() -> e);
              }
            });
  }
}
