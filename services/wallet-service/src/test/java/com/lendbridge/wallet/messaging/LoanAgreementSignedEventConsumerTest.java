package com.lendbridge.wallet.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.loan.avro.LoanAgreementSigned;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import com.lendbridge.wallet.model.Wallet;
import com.lendbridge.wallet.outbox.KafkaTopics;
import com.lendbridge.wallet.outbox.WalletBorrowerAccountAssembledPayload;
import com.lendbridge.wallet.repository.ProcessedEventRepository;
import com.lendbridge.wallet.service.WalletService;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanAgreementSignedEventConsumerTest {

  @Mock private WalletService walletService;
  @Mock private ProcessedEventRepository processedEventRepository;
  @Mock private OutboxEventWriter outboxEventWriter;

  private LoanAgreementSignedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new LoanAgreementSignedEventConsumer(walletService, processedEventRepository, outboxEventWriter);
  }

  private static ConsumerRecord<String, LoanAgreementSigned> recordWith(String loanId, String borrowerUserId) {
    LoanAgreementSigned payload =
        LoanAgreementSigned.newBuilder()
            .setLoanId(loanId)
            .setBorrowerUserId(borrowerUserId)
            .setAmountKobo(100_000L)
            .setOriginationFeeKobo(2_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    return new ConsumerRecord<>("loan.agreement.signed", 0, 0L, loanId, payload);
  }

  @Test
  void provisioningSucceeds_publishesAssembledSuccess() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    String loanId = UUID.randomUUID().toString();
    UUID borrowerUserId = UUID.randomUUID();
    UUID ledgerAccountId = UUID.randomUUID();
    Wallet wallet = Wallet.builder().id(UUID.randomUUID()).userId(borrowerUserId).ledgerAccountId(ledgerAccountId).build();
    when(walletService.getOrCreateWallet(borrowerUserId)).thenReturn(wallet);

    consumer.onSigned(recordWith(loanId, borrowerUserId.toString()), "event-1".getBytes());

    ArgumentCaptor<WalletBorrowerAccountAssembledPayload> captor =
        ArgumentCaptor.forClass(WalletBorrowerAccountAssembledPayload.class);
    verify(outboxEventWriter)
        .write(eq("Loan"), eq(loanId), eq(KafkaTopics.WALLET_BORROWER_ACCOUNT_ASSEMBLED), captor.capture());
    WalletBorrowerAccountAssembledPayload payload = captor.getValue();
    assertThat(payload.isSuccess()).isTrue();
    assertThat(payload.getBorrowerLedgerAccountId()).isEqualTo(ledgerAccountId.toString());
    assertThat(payload.getFailureReason()).isNull();
    verify(processedEventRepository).save(any());
  }

  @Test
  void provisioningFails_publishesAssembledFailure() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    String loanId = UUID.randomUUID().toString();
    UUID borrowerUserId = UUID.randomUUID();
    when(walletService.getOrCreateWallet(borrowerUserId)).thenThrow(new IllegalStateException("ledger unavailable"));

    consumer.onSigned(recordWith(loanId, borrowerUserId.toString()), "event-2".getBytes());

    ArgumentCaptor<WalletBorrowerAccountAssembledPayload> captor =
        ArgumentCaptor.forClass(WalletBorrowerAccountAssembledPayload.class);
    verify(outboxEventWriter)
        .write(eq("Loan"), eq(loanId), eq(KafkaTopics.WALLET_BORROWER_ACCOUNT_ASSEMBLED), captor.capture());
    WalletBorrowerAccountAssembledPayload payload = captor.getValue();
    assertThat(payload.isSuccess()).isFalse();
    assertThat(payload.getBorrowerLedgerAccountId()).isNull();
    assertThat(payload.getFailureReason()).isEqualTo("ledger unavailable");
    verify(processedEventRepository).save(any());
  }

  @Test
  void alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-3")).thenReturn(true);

    consumer.onSigned(
        recordWith(UUID.randomUUID().toString(), UUID.randomUUID().toString()), "event-3".getBytes());

    verify(walletService, never()).getOrCreateWallet(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
    verify(processedEventRepository, never()).save(any());
  }
}
