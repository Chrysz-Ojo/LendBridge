package com.lendbridge.investment.fundingsaga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.investment.model.Commitment;
import com.lendbridge.investment.model.CommitmentStatus;
import com.lendbridge.investment.model.DisbursementAssembly;
import com.lendbridge.investment.outbox.InvestmentDisbursementCompensatePayload;
import com.lendbridge.investment.outbox.InvestmentDisbursementReadyPayload;
import com.lendbridge.investment.outbox.InvestmentHoldsCaptureRequestedPayload;
import com.lendbridge.investment.outbox.KafkaTopics;
import com.lendbridge.investment.repository.CommitmentRepository;
import com.lendbridge.investment.repository.DisbursementAssemblyRepository;
import com.lendbridge.starter.outbox.OutboxEventWriter;
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
class DisbursementAssemblerTest {

  @Mock private DisbursementAssemblyRepository assemblyRepository;
  @Mock private CommitmentRepository commitmentRepository;
  @Mock private OutboxEventWriter outboxEventWriter;

  private DisbursementAssembler assembler;

  @BeforeEach
  void setUp() {
    assembler = new DisbursementAssembler(assemblyRepository, commitmentRepository, outboxEventWriter);
  }

  private Commitment activeCommitment(UUID loanId, long amountKobo, UUID holdId, UUID lenderLedgerAccountId) {
    return Commitment.builder()
        .id(UUID.randomUUID())
        .loanId(loanId)
        .amountKobo(amountKobo)
        .holdId(holdId)
        .lenderLedgerAccountId(lenderLedgerAccountId)
        .status(CommitmentStatus.ACTIVE)
        .build();
  }

  @Test
  void onAgreementSigned_requestsHoldCaptureForActiveCommitments_andDoesNotCompleteYet() {
    UUID loanId = UUID.randomUUID();
    UUID holdId = UUID.randomUUID();
    when(assemblyRepository.findById(loanId)).thenReturn(Optional.empty());
    when(assemblyRepository.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(commitmentRepository.findAllByLoanIdAndStatus(loanId, CommitmentStatus.ACTIVE))
        .thenReturn(List.of(activeCommitment(loanId, 600_000L, holdId, UUID.randomUUID())));

    assembler.onAgreementSigned(loanId, 12_000L);

    ArgumentCaptor<InvestmentHoldsCaptureRequestedPayload> captor =
        ArgumentCaptor.forClass(InvestmentHoldsCaptureRequestedPayload.class);
    verify(outboxEventWriter)
        .write(eq("Loan"), eq(loanId.toString()), eq(KafkaTopics.INVESTMENT_HOLDS_CAPTURE_REQUESTED), captor.capture());
    assertThat(captor.getValue().getHoldIds()).containsExactly(holdId.toString());
    assertThat(captor.getValue().getTotalKobo()).isEqualTo(600_000L);
    verify(outboxEventWriter, never())
        .write(any(), any(), eq(KafkaTopics.INVESTMENT_DISBURSEMENT_READY), any());
    verify(assemblyRepository, never()).delete(any());
  }

  @Test
  void allThreeLegsSucceed_publishesDisbursementReady_andDeletesRow() {
    UUID loanId = UUID.randomUUID();
    UUID holdId = UUID.randomUUID();
    UUID lenderLedgerAccountId = UUID.randomUUID();
    UUID borrowerLedgerAccountId = UUID.randomUUID();

    DisbursementAssembly row =
        DisbursementAssembly.builder().loanId(loanId).signedReceived(true).originationFeeKobo(12_000L).build();
    when(assemblyRepository.findById(loanId)).thenReturn(Optional.of(row));
    when(assemblyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(commitmentRepository.findAllByLoanIdAndStatus(loanId, CommitmentStatus.ACTIVE))
        .thenReturn(List.of(activeCommitment(loanId, 600_000L, holdId, lenderLedgerAccountId)));

    assembler.onHoldsCaptureAssembled(loanId, true, null);
    assembler.onBorrowerAccountAssembled(loanId, true, borrowerLedgerAccountId, null);

    ArgumentCaptor<InvestmentDisbursementReadyPayload> captor =
        ArgumentCaptor.forClass(InvestmentDisbursementReadyPayload.class);
    verify(outboxEventWriter)
        .write(eq("Loan"), eq(loanId.toString()), eq(KafkaTopics.INVESTMENT_DISBURSEMENT_READY), captor.capture());
    InvestmentDisbursementReadyPayload payload = captor.getValue();
    assertThat(payload.getBorrowerLedgerAccountId()).isEqualTo(borrowerLedgerAccountId.toString());
    assertThat(payload.getOriginationFeeKobo()).isEqualTo(12_000L);
    assertThat(payload.getTotalKobo()).isEqualTo(600_000L);
    assertThat(payload.getLegs()).hasSize(1);
    assertThat(payload.getLegs().get(0).getLenderLedgerAccountId()).isEqualTo(lenderLedgerAccountId.toString());
    verify(assemblyRepository).delete(row);
  }

  @Test
  void holdsCaptureFails_publishesCompensate_doesNotPublishReady() {
    UUID loanId = UUID.randomUUID();
    UUID holdId = UUID.randomUUID();

    DisbursementAssembly row =
        DisbursementAssembly.builder().loanId(loanId).signedReceived(true).originationFeeKobo(12_000L).build();
    when(assemblyRepository.findById(loanId)).thenReturn(Optional.of(row));
    when(assemblyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(commitmentRepository.findAllByLoanIdAndStatus(loanId, CommitmentStatus.ACTIVE))
        .thenReturn(List.of(activeCommitment(loanId, 600_000L, holdId, UUID.randomUUID())));

    assembler.onHoldsCaptureAssembled(loanId, false, "hold not active");
    assembler.onBorrowerAccountAssembled(loanId, true, UUID.randomUUID(), null);

    ArgumentCaptor<InvestmentDisbursementCompensatePayload> captor =
        ArgumentCaptor.forClass(InvestmentDisbursementCompensatePayload.class);
    verify(outboxEventWriter)
        .write(eq("Loan"), eq(loanId.toString()), eq(KafkaTopics.INVESTMENT_DISBURSEMENT_COMPENSATE), captor.capture());
    assertThat(captor.getValue().getHoldIds()).containsExactly(holdId.toString());
    verify(outboxEventWriter, never())
        .write(any(), any(), eq(KafkaTopics.INVESTMENT_DISBURSEMENT_READY), any());
    verify(assemblyRepository).delete(row);
  }

  @Test
  void onLedgerDisbursementFailed_publishesCompensateFromActiveCommitments() {
    UUID loanId = UUID.randomUUID();
    UUID holdId = UUID.randomUUID();
    when(commitmentRepository.findAllByLoanIdAndStatus(loanId, CommitmentStatus.ACTIVE))
        .thenReturn(List.of(activeCommitment(loanId, 600_000L, holdId, UUID.randomUUID())));

    assembler.onLedgerDisbursementFailed(loanId);

    ArgumentCaptor<InvestmentDisbursementCompensatePayload> captor =
        ArgumentCaptor.forClass(InvestmentDisbursementCompensatePayload.class);
    verify(outboxEventWriter)
        .write(eq("Loan"), eq(loanId.toString()), eq(KafkaTopics.INVESTMENT_DISBURSEMENT_COMPENSATE), captor.capture());
    assertThat(captor.getValue().getHoldIds()).containsExactly(holdId.toString());
    verify(assemblyRepository, never()).delete(any());
  }

  @Test
  void onlyTwoOfThreeLegsArrived_doesNotActYet() {
    UUID loanId = UUID.randomUUID();
    DisbursementAssembly row = DisbursementAssembly.builder().loanId(loanId).signedReceived(true).build();
    when(assemblyRepository.findById(loanId)).thenReturn(Optional.of(row));
    when(assemblyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    assembler.onHoldsCaptureAssembled(loanId, true, null);

    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
    verify(assemblyRepository, never()).delete(any());
  }
}
