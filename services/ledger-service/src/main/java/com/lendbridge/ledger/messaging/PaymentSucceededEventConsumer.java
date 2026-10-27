package com.lendbridge.ledger.messaging;

import com.lendbridge.ledger.ClearingAccounts;
import com.lendbridge.ledger.dto.PostJournalEntryRequest;
import com.lendbridge.ledger.dto.PostingRequest;
import com.lendbridge.ledger.model.DebitCredit;
import com.lendbridge.ledger.model.ProcessedEvent;
import com.lendbridge.ledger.repository.ProcessedEventRepository;
import com.lendbridge.ledger.service.JournalEntryService;
import com.lendbridge.payment.avro.PaymentSucceeded;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Posts the actual ledger journal entry for a successful top-up or payout — this is how "only the
 * Ledger Service can move money" holds even though Payment never calls Ledger directly. Called
 * in-process (plain bean-to-bean, not over HTTP): a Kafka consumer has no inbound caller JWT, but
 * unlike wallet-service's lazy ledger-account provisioning (ADR 0008), that's not a problem here
 * — this consumer invokes {@link JournalEntryService} as a local Spring bean, so there is no
 * cross-service auth to solve (ADR 0013). The journal entry's own idempotency key is
 * payment-service's own {@code idempotencyKey}, reused verbatim, so a redelivered event can never
 * double-post even bypassing {@code processed_event}'s own dedup check below.
 */
@Component
@RequiredArgsConstructor
public class PaymentSucceededEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(PaymentSucceededEventConsumer.class);
  private static final String TOP_UP = "TOP_UP";
  private static final String PAYOUT = "PAYOUT";

  private final JournalEntryService journalEntryService;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "payment.succeeded", groupId = "ledger-service")
  @Transactional
  public void onPaymentSucceeded(
      ConsumerRecord<String, PaymentSucceeded> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed payment.succeeded event {}", eventId);
      return;
    }

    postJournalEntry(record.value());

    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }

  private void postJournalEntry(PaymentSucceeded payload) {
    String purpose = payload.getPurpose();
    DebitCredit walletDirection;
    DebitCredit clearingDirection;
    if (TOP_UP.equals(purpose)) {
      walletDirection = DebitCredit.CREDIT;
      clearingDirection = DebitCredit.DEBIT;
    } else if (PAYOUT.equals(purpose)) {
      walletDirection = DebitCredit.DEBIT;
      clearingDirection = DebitCredit.CREDIT;
    } else {
      // Forward-compatible with a future REPAYMENT purpose once Loan-service exists — not an
      // error, just nothing this consumer knows how to post yet.
      log.info("Ignoring payment.succeeded with unrecognized purpose {}", purpose);
      return;
    }

    UUID walletAccountId = UUID.fromString(payload.getWalletLedgerAccountId());

    PostJournalEntryRequest request = new PostJournalEntryRequest();
    request.setCurrency(payload.getCurrency());
    request.setReference(payload.getPaymentReferenceId());
    request.setDescription("payment." + purpose.toLowerCase());
    request.setPostings(
        List.of(
            new PostingRequest(walletAccountId, walletDirection, payload.getAmountKobo()),
            new PostingRequest(
                ClearingAccounts.PAYMENT_CLEARING_NGN, clearingDirection, payload.getAmountKobo())));

    journalEntryService.postJournalEntry(request, payload.getIdempotencyKey());
  }
}
