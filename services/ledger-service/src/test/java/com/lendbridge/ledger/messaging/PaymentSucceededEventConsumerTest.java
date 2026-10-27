package com.lendbridge.ledger.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.ledger.ClearingAccounts;
import com.lendbridge.ledger.dto.PostJournalEntryRequest;
import com.lendbridge.ledger.model.DebitCredit;
import com.lendbridge.ledger.repository.ProcessedEventRepository;
import com.lendbridge.ledger.service.JournalEntryService;
import com.lendbridge.payment.avro.PaymentSucceeded;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentSucceededEventConsumerTest {

  @Mock private JournalEntryService journalEntryService;
  @Mock private ProcessedEventRepository processedEventRepository;

  private PaymentSucceededEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new PaymentSucceededEventConsumer(journalEntryService, processedEventRepository);
  }

  @Test
  void topUp_creditsWalletAccount_debitsClearingAccount() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID walletAccountId = UUID.randomUUID();
    PaymentSucceeded payload =
        PaymentSucceeded.newBuilder()
            .setPaymentReferenceId("pay-1")
            .setPurpose("TOP_UP")
            .setWalletLedgerAccountId(walletAccountId.toString())
            .setAmountKobo(5_000L)
            .setCurrency("NGN")
            .setIdempotencyKey("idem-1")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, PaymentSucceeded> record =
        new ConsumerRecord<>("payment.succeeded", 0, 0L, "pay-1", payload);

    consumer.onPaymentSucceeded(record, "event-1".getBytes());

    ArgumentCaptor<PostJournalEntryRequest> captor = ArgumentCaptor.forClass(PostJournalEntryRequest.class);
    verify(journalEntryService).postJournalEntry(captor.capture(), eq("idem-1"));
    PostJournalEntryRequest request = captor.getValue();
    assertThat(request.getCurrency()).isEqualTo("NGN");
    assertThat(request.getPostings()).hasSize(2);
    assertThat(request.getPostings())
        .anySatisfy(
            p -> {
              assertThat(p.getAccountId()).isEqualTo(walletAccountId);
              assertThat(p.getDirection()).isEqualTo(DebitCredit.CREDIT);
            })
        .anySatisfy(
            p -> {
              assertThat(p.getAccountId()).isEqualTo(ClearingAccounts.PAYMENT_CLEARING_NGN);
              assertThat(p.getDirection()).isEqualTo(DebitCredit.DEBIT);
            });
  }

  @Test
  void payout_debitsWalletAccount_creditsClearingAccount() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID walletAccountId = UUID.randomUUID();
    PaymentSucceeded payload =
        PaymentSucceeded.newBuilder()
            .setPaymentReferenceId("pay-2")
            .setPurpose("PAYOUT")
            .setWalletLedgerAccountId(walletAccountId.toString())
            .setAmountKobo(5_000L)
            .setCurrency("NGN")
            .setIdempotencyKey("idem-2")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, PaymentSucceeded> record =
        new ConsumerRecord<>("payment.succeeded", 0, 0L, "pay-2", payload);

    consumer.onPaymentSucceeded(record, "event-2".getBytes());

    ArgumentCaptor<PostJournalEntryRequest> captor = ArgumentCaptor.forClass(PostJournalEntryRequest.class);
    verify(journalEntryService).postJournalEntry(captor.capture(), eq("idem-2"));
    assertThat(captor.getValue().getPostings())
        .anySatisfy(
            p -> {
              assertThat(p.getAccountId()).isEqualTo(walletAccountId);
              assertThat(p.getDirection()).isEqualTo(DebitCredit.DEBIT);
            })
        .anySatisfy(
            p -> {
              assertThat(p.getAccountId()).isEqualTo(ClearingAccounts.PAYMENT_CLEARING_NGN);
              assertThat(p.getDirection()).isEqualTo(DebitCredit.CREDIT);
            });
  }

  @Test
  void unrecognizedPurpose_isIgnored_notAnError() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    PaymentSucceeded payload =
        PaymentSucceeded.newBuilder()
            .setPaymentReferenceId("pay-3")
            .setPurpose("REPAYMENT")
            .setWalletLedgerAccountId(UUID.randomUUID().toString())
            .setAmountKobo(1_000L)
            .setCurrency("NGN")
            .setIdempotencyKey("idem-3")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, PaymentSucceeded> record =
        new ConsumerRecord<>("payment.succeeded", 0, 0L, "pay-3", payload);

    consumer.onPaymentSucceeded(record, "event-3".getBytes());

    verify(journalEntryService, never()).postJournalEntry(any(), any());
    verify(processedEventRepository).save(any());
  }

  @Test
  void alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-4")).thenReturn(true);
    PaymentSucceeded payload =
        PaymentSucceeded.newBuilder()
            .setPaymentReferenceId("pay-4")
            .setPurpose("TOP_UP")
            .setWalletLedgerAccountId(UUID.randomUUID().toString())
            .setAmountKobo(1_000L)
            .setCurrency("NGN")
            .setIdempotencyKey("idem-4")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, PaymentSucceeded> record =
        new ConsumerRecord<>("payment.succeeded", 0, 0L, "pay-4", payload);

    consumer.onPaymentSucceeded(record, "event-4".getBytes());

    verify(journalEntryService, never()).postJournalEntry(any(), any());
    verify(processedEventRepository, never()).save(any());
  }
}
