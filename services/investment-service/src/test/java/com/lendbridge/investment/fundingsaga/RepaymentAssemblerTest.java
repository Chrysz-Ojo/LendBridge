package com.lendbridge.investment.fundingsaga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.model.Commitment;
import com.lendbridge.investment.model.CommitmentStatus;
import com.lendbridge.investment.model.RepaymentAssembly;
import com.lendbridge.investment.outbox.InvestmentRepaymentFailedPayload;
import com.lendbridge.investment.outbox.InvestmentRepaymentReadyPayload;
import com.lendbridge.investment.outbox.KafkaTopics;
import com.lendbridge.investment.repository.CommitmentRepository;
import com.lendbridge.investment.repository.RepaymentAssemblyRepository;
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
class RepaymentAssemblerTest {

  @Mock private RepaymentAssemblyRepository assemblyRepository;
  @Mock private CommitmentRepository commitmentRepository;
  @Mock private OutboxEventWriter outboxEventWriter;

  private RepaymentAssembler assembler;

  @BeforeEach
  void setUp() {
    assembler = new RepaymentAssembler(assemblyRepository, commitmentRepository, outboxEventWriter);
  }

  private Commitment activeCommitment(UUID loanId, long amountKobo, UUID lenderLedgerAccountId, Instant createdAt) {
    return Commitment.builder()
        .id(UUID.randomUUID())
        .loanId(loanId)
        .amountKobo(amountKobo)
        .lenderLedgerAccountId(lenderLedgerAccountId)
        .status(CommitmentStatus.ACTIVE)
        .createdAt(createdAt)
        .build();
  }

  @Test
  void onRepaymentRequested_doesNotActUntilAccountConfirmed() {
    UUID instalmentId = UUID.randomUUID();
    UUID loanId = UUID.randomUUID();
    when(assemblyRepository.findById(instalmentId)).thenReturn(Optional.empty());
    when(assemblyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    assembler.onRepaymentRequested(instalmentId, loanId, 85_000L);

    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
    verify(assemblyRepository, never()).delete(any());
  }

  @Test
  void bothLegsSucceed_publishesReadyWithProRataLegs_lastLenderAbsorbsRemainder() {
    UUID instalmentId = UUID.randomUUID();
    UUID loanId = UUID.randomUUID();
    UUID borrowerLedgerAccountId = UUID.randomUUID();
    UUID lenderA = UUID.randomUUID();
    UUID lenderB = UUID.randomUUID();

    RepaymentAssembly row =
        RepaymentAssembly.builder().instalmentId(instalmentId).loanId(loanId).requestedReceived(true).totalKobo(100L).build();
    when(assemblyRepository.findById(instalmentId)).thenReturn(Optional.of(row));
    when(assemblyRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(commitmentRepository.findAllByLoanIdAndStatus(loanId, CommitmentStatus.ACTIVE))
        .thenReturn(
            List.of(
                activeCommitment(loanId, 300_000L, lenderA, Instant.ofEpochMilli(1)),
                activeCommitment(loanId, 700_000L, lenderB, Instant.ofEpochMilli(2))));

    assembler.onAccountConfirmed(instalmentId, loanId, true, borrowerLedgerAccountId, null);

    ArgumentCaptor<InvestmentRepaymentReadyPayload> captor =
        ArgumentCaptor.forClass(InvestmentRepaymentReadyPayload.class);
    verify(outboxEventWriter)
        .write(eq("Loan"), eq(loanId.toString()), eq(KafkaTopics.INVESTMENT_REPAYMENT_READY), captor.capture());
    InvestmentRepaymentReadyPayload payload = captor.getValue();
    assertThat(payload.getInstalmentId()).isEqualTo(instalmentId.toString());
    assertThat(payload.getBorrowerLedgerAccountId()).isEqualTo(borrowerLedgerAccountId.toString());
    assertThat(payload.getLegs()).hasSize(2);
    assertThat(payload.getLegs().get(0).getLenderLedgerAccountId()).isEqualTo(lenderA.toString());
    assertThat(payload.getLegs().get(0).getAmountKobo()).isEqualTo(30L); // 30% of 100
    assertThat(payload.getLegs().get(1).getLenderLedgerAccountId()).isEqualTo(lenderB.toString());
    assertThat(payload.getLegs().get(1).getAmountKobo()).isEqualTo(70L); // remainder absorbed
    long total = payload.getLegs().stream().mapToLong(l -> l.getAmountKobo()).sum();
    assertThat(total).isEqualTo(100L);
    verify(assemblyRepository).delete(row);
  }

  @Test
  void accountConfirmationFails_publishesRepaymentFailed_noReadyEvent() {
    UUID instalmentId = UUID.randomUUID();
    UUID loanId = UUID.randomUUID();

    RepaymentAssembly row =
        RepaymentAssembly.builder().instalmentId(instalmentId).loanId(loanId).requestedReceived(true).totalKobo(100L).build();
    when(assemblyRepository.findById(instalmentId)).thenReturn(Optional.of(row));
    when(assemblyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    assembler.onAccountConfirmed(instalmentId, loanId, false, null, "borrower wallet not provisioned");

    ArgumentCaptor<InvestmentRepaymentFailedPayload> captor =
        ArgumentCaptor.forClass(InvestmentRepaymentFailedPayload.class);
    verify(outboxEventWriter)
        .write(eq("Loan"), eq(loanId.toString()), eq(KafkaTopics.INVESTMENT_REPAYMENT_FAILED), captor.capture());
    assertThat(captor.getValue().getReason()).isEqualTo("borrower wallet not provisioned");
    verify(outboxEventWriter, never())
        .write(any(), any(), eq(KafkaTopics.INVESTMENT_REPAYMENT_READY), any());
    verify(assemblyRepository).delete(row);
  }
}
