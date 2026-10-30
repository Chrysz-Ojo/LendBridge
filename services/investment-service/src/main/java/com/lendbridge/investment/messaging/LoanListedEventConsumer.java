package com.lendbridge.investment.messaging;

import com.lendbridge.investment.model.LoanListing;
import com.lendbridge.investment.model.ProcessedEvent;
import com.lendbridge.investment.repository.LoanListingRepository;
import com.lendbridge.investment.repository.ProcessedEventRepository;
import com.lendbridge.loan.avro.LoanListed;
import java.time.Instant;
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
 * Builds this service's own {@code LoanListing} projection — same consumer/idempotency shape as
 * ledger-service's {@code PaymentSucceededEventConsumer} (ADR 0013) and marketplace-service's own
 * consumer of the same topic. Deliberately a second, independent projection rather than a call to
 * marketplace-service or loan-service: commitment validation (remaining-unfunded amount,
 * borrower-exposure cap) needs to run off this service's own data (docs/adr/0017).
 */
@Component
@RequiredArgsConstructor
public class LoanListedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(LoanListedEventConsumer.class);

  private final LoanListingRepository loanListingRepository;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "loan.listed", groupId = "investment-service")
  @Transactional
  public void onLoanListed(
      ConsumerRecord<String, LoanListed> record, @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed loan.listed event {}", eventId);
      return;
    }

    LoanListed payload = record.value();
    LoanListing listing =
        LoanListing.builder()
            .id(UUID.fromString(payload.getLoanId().toString()))
            .borrowerUserId(UUID.fromString(payload.getBorrowerUserId().toString()))
            .amountKobo(payload.getAmountKobo())
            .active(true)
            .build();
    loanListingRepository.save(listing);

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
