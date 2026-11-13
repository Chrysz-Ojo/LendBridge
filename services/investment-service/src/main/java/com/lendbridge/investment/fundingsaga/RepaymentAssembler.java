package com.lendbridge.investment.fundingsaga;

import com.lendbridge.investment.model.Commitment;
import com.lendbridge.investment.model.CommitmentStatus;
import com.lendbridge.investment.model.RepaymentAssembly;
import com.lendbridge.investment.outbox.InvestmentRepaymentFailedPayload;
import com.lendbridge.investment.outbox.InvestmentRepaymentReadyPayload;
import com.lendbridge.investment.outbox.KafkaTopics;
import com.lendbridge.investment.outbox.RepaymentLegPayload;
import com.lendbridge.investment.repository.CommitmentRepository;
import com.lendbridge.investment.repository.RepaymentAssemblyRepository;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The repayment saga's aggregator — same two-independent-legs-join shape as
 * {@link DisbursementAssembler}, keyed by {@code instalmentId} instead of {@code loanId} (a loan
 * only ever has one instalment in flight at a time — {@code InstalmentStatus.REQUESTED} guards
 * that on loan-service's side — but a stable, specific key is still the right one to join on).
 * Unlike disbursement, there's no holds leg: just the amount ({@link #onRepaymentRequested}) and
 * the borrower-account confirmation ({@link #onAccountConfirmed}).
 */
@Component
public class RepaymentAssembler {

  private final RepaymentAssemblyRepository assemblyRepository;
  private final CommitmentRepository commitmentRepository;
  private final OutboxEventWriter outboxEventWriter;

  public RepaymentAssembler(
      RepaymentAssemblyRepository assemblyRepository,
      CommitmentRepository commitmentRepository,
      OutboxEventWriter outboxEventWriter) {
    this.assemblyRepository = assemblyRepository;
    this.commitmentRepository = commitmentRepository;
    this.outboxEventWriter = outboxEventWriter;
  }

  @Transactional
  public void onRepaymentRequested(UUID instalmentId, UUID loanId, long amountKobo) {
    RepaymentAssembly assembly = findOrCreate(instalmentId, loanId);
    assembly.setRequestedReceived(true);
    assembly.setTotalKobo(amountKobo);
    assemblyRepository.save(assembly);
    tryComplete(assembly);
  }

  @Transactional
  public void onAccountConfirmed(
      UUID instalmentId, UUID loanId, boolean success, UUID borrowerLedgerAccountId, String failureReason) {
    RepaymentAssembly assembly = findOrCreate(instalmentId, loanId);
    assembly.setAccountConfirmed(success);
    assembly.setBorrowerLedgerAccountId(borrowerLedgerAccountId);
    assembly.setFailureReason(failureReason);
    assemblyRepository.save(assembly);
    tryComplete(assembly);
  }

  private void tryComplete(RepaymentAssembly assembly) {
    if (!assembly.isComplete()) {
      return;
    }
    if (Boolean.TRUE.equals(assembly.getAccountConfirmed())) {
      publishReady(assembly);
    } else {
      publishFailed(assembly);
    }
    assemblyRepository.delete(assembly);
  }

  private void publishReady(RepaymentAssembly assembly) {
    List<RepaymentLegPayload> legs = proRataLegs(assembly.getLoanId(), assembly.getTotalKobo());
    outboxEventWriter.write(
        "Loan",
        assembly.getLoanId().toString(),
        KafkaTopics.INVESTMENT_REPAYMENT_READY,
        new InvestmentRepaymentReadyPayload(
            assembly.getLoanId().toString(),
            assembly.getInstalmentId().toString(),
            assembly.getBorrowerLedgerAccountId().toString(),
            assembly.getTotalKobo(),
            legs,
            Instant.now().toEpochMilli()));
  }

  private void publishFailed(RepaymentAssembly assembly) {
    outboxEventWriter.write(
        "Loan",
        assembly.getLoanId().toString(),
        KafkaTopics.INVESTMENT_REPAYMENT_FAILED,
        new InvestmentRepaymentFailedPayload(
            assembly.getLoanId().toString(),
            assembly.getInstalmentId().toString(),
            assembly.getFailureReason(),
            Instant.now().toEpochMilli()));
  }

  /** Pro-rata by original commitment size, rounded {@code HALF_EVEN} same as
   * {@code RepaymentScheduleCalculator}; the most-recently-committed lender absorbs whatever
   * rounding remainder is left, so the legs always sum to exactly {@code totalKobo}. */
  private List<RepaymentLegPayload> proRataLegs(UUID loanId, long totalKobo) {
    List<Commitment> commitments =
        commitmentRepository.findAllByLoanIdAndStatus(loanId, CommitmentStatus.ACTIVE).stream()
            .sorted(Comparator.comparing(Commitment::getCreatedAt))
            .toList();
    long totalCommittedKobo = commitments.stream().mapToLong(Commitment::getAmountKobo).sum();

    List<RepaymentLegPayload> legs = new ArrayList<>(commitments.size());
    long allocated = 0;
    for (int i = 0; i < commitments.size(); i++) {
      Commitment commitment = commitments.get(i);
      long shareKobo;
      if (i == commitments.size() - 1) {
        shareKobo = totalKobo - allocated;
      } else {
        shareKobo =
            BigDecimal.valueOf(commitment.getAmountKobo())
                .multiply(BigDecimal.valueOf(totalKobo))
                .divide(BigDecimal.valueOf(totalCommittedKobo), 0, RoundingMode.HALF_EVEN)
                .longValueExact();
        allocated += shareKobo;
      }
      legs.add(new RepaymentLegPayload(commitment.getLenderLedgerAccountId().toString(), shareKobo));
    }
    return legs;
  }

  /** Same race-handling idiom as {@link DisbursementAssembler#findOrCreate} — the two legs are
   * triggered by independent upstream events, so a lost insert race is resolved by re-reading. */
  private RepaymentAssembly findOrCreate(UUID instalmentId, UUID loanId) {
    return assemblyRepository
        .findById(instalmentId)
        .orElseGet(
            () -> {
              try {
                return assemblyRepository.save(
                    RepaymentAssembly.builder()
                        .instalmentId(instalmentId)
                        .loanId(loanId)
                        .createdAt(Instant.now())
                        .build());
              } catch (DataIntegrityViolationException e) {
                return assemblyRepository.findById(instalmentId).orElseThrow(() -> e);
              }
            });
  }
}
