package com.lendbridge.wallet.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lendbridge.loan.avro.LoanRepaymentRequested;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import com.lendbridge.wallet.model.Wallet;
import com.lendbridge.wallet.outbox.KafkaTopics;
import com.lendbridge.wallet.outbox.WalletRepaymentAccountConfirmedPayload;
import com.lendbridge.wallet.repository.ProcessedEventRepository;
import com.lendbridge.wallet.service.WalletService;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanRepaymentRequestedEventConsumerTest {

  @Mock private WalletService walletService;
  @Mock private ProcessedEventRepository processedEventRepository;
  @Mock private OutboxEventWriter outboxEventWriter;

  private LoanRepaymentRequestedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new LoanRepaymentRequestedEventConsumer(walletService, processedEventRepository, outboxEventWriter);
  }

  private static ConsumerRecord<String, LoanRepaymentRequested> recordWith(
      String loanId, String instalmentId, String borrowerUserId) {
    LoanRepaymentRequested payload =
        LoanRepaymentRequested.newBuilder()
            .setLoanId(loanId)
            .setInstalmentId(instalmentId)
            .setBorrowerUserId(borrowerUserId)
            .setAmountKobo(85_000L)
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    return new ConsumerRecord<>("loan.repayment.requested", 0, 0L, loanId, payload);
  }

  @Test
  void walletProvisioned_confirmsSuccess() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    String loanId = UUID.randomUUID().toString();
    String instalmentId = UUID.randomUUID().toString();
    UUID borrowerUserId = UUID.randomUUID();
    UUID ledgerAccountId = UUID.randomUUID();
    Wallet wallet = Wallet.builder().id(UUID.randomUUID()).userId(borrowerUserId).ledgerAccountId(ledgerAccountId).build();
    when(walletService.findWallet(borrowerUserId)).thenReturn(Optional.of(wallet));

    consumer.onRepaymentRequested(recordWith(loanId, instalmentId, borrowerUserId.toString()), "event-1".getBytes());

    ArgumentCaptor<WalletRepaymentAccountConfirmedPayload> captor =
        ArgumentCaptor.forClass(WalletRepaymentAccountConfirmedPayload.class);
    verify(outboxEventWriter)
        .write(
            org.mockito.ArgumentMatchers.eq("Loan"),
            org.mockito.ArgumentMatchers.eq(loanId),
            org.mockito.ArgumentMatchers.eq(KafkaTopics.WALLET_REPAYMENT_ACCOUNT_CONFIRMED),
            captor.capture());
    assertThat(captor.getValue().isSuccess()).isTrue();
    assertThat(captor.getValue().getBorrowerLedgerAccountId()).isEqualTo(ledgerAccountId.toString());
    verify(processedEventRepository).save(any());
  }

  @Test
  void walletMissing_confirmsFailure() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    String loanId = UUID.randomUUID().toString();
    String instalmentId = UUID.randomUUID().toString();
    UUID borrowerUserId = UUID.randomUUID();
    when(walletService.findWallet(borrowerUserId)).thenReturn(Optional.empty());

    consumer.onRepaymentRequested(recordWith(loanId, instalmentId, borrowerUserId.toString()), "event-2".getBytes());

    ArgumentCaptor<WalletRepaymentAccountConfirmedPayload> captor =
        ArgumentCaptor.forClass(WalletRepaymentAccountConfirmedPayload.class);
    verify(outboxEventWriter)
        .write(
            org.mockito.ArgumentMatchers.eq("Loan"),
            org.mockito.ArgumentMatchers.eq(loanId),
            org.mockito.ArgumentMatchers.eq(KafkaTopics.WALLET_REPAYMENT_ACCOUNT_CONFIRMED),
            captor.capture());
    assertThat(captor.getValue().isSuccess()).isFalse();
    assertThat(captor.getValue().getBorrowerLedgerAccountId()).isNull();
  }

  @Test
  void alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-3")).thenReturn(true);

    consumer.onRepaymentRequested(
        recordWith(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString()),
        "event-3".getBytes());

    verifyNoInteractions(walletService, outboxEventWriter);
  }
}
