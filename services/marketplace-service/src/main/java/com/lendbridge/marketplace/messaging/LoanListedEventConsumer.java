package com.lendbridge.marketplace.messaging;

import com.lendbridge.loan.avro.LoanListed;
import com.lendbridge.marketplace.model.Listing;
import com.lendbridge.marketplace.model.ListingStatus;
import com.lendbridge.marketplace.model.ProcessedEvent;
import com.lendbridge.marketplace.repository.ListingRepository;
import com.lendbridge.marketplace.repository.ProcessedEventRepository;
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
 * Builds this service's read model — same consumer/idempotency shape as ledger-service's
 * {@code PaymentSucceededEventConsumer} (ADR 0013). {@code loan.listed} is consumed in-process
 * (plain bean call, no HTTP), so there is no cross-service auth to solve; the listing's id is the
 * loan's own id, reused verbatim, which is what makes the upsert below idempotent on redelivery
 * even without the {@code processed_event} check.
 */
@Component
@RequiredArgsConstructor
public class LoanListedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(LoanListedEventConsumer.class);

  private final ListingRepository listingRepository;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "loan.listed", groupId = "marketplace-service")
  @Transactional
  public void onLoanListed(
      ConsumerRecord<String, LoanListed> record, @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed loan.listed event {}", eventId);
      return;
    }

    LoanListed payload = record.value();
    Instant now = Instant.now();
    Listing listing =
        Listing.builder()
            .id(UUID.fromString(payload.getLoanId().toString()))
            .borrowerUserId(UUID.fromString(payload.getBorrowerUserId().toString()))
            .amountKobo(payload.getAmountKobo())
            .interestRateBps(payload.getInterestRateBps())
            .tenorMonths(payload.getTenorMonths())
            .purpose(payload.getPurpose().toString())
            .status(ListingStatus.ACTIVE)
            .listedAt(Instant.ofEpochMilli(payload.getOccurredAtEpochMillis()))
            .createdAt(now)
            .updatedAt(now)
            .build();
    listingRepository.save(listing);

    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(now).build());
  }
}
