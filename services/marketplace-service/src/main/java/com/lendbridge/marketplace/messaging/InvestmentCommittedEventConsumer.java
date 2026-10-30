package com.lendbridge.marketplace.messaging;

import com.lendbridge.investment.avro.InvestmentCommitted;
import com.lendbridge.marketplace.model.Listing;
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

/** Keeps {@code Listing.fundedAmountKobo} current for the browse view (PRD E3-S2's "amount
 * funded so far") — investment-service remains the system of record for the underlying
 * commitments; this is purely a denormalized display field, same read-model-only reasoning as
 * {@code LoanListedEventConsumer}. */
@Component
@RequiredArgsConstructor
public class InvestmentCommittedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(InvestmentCommittedEventConsumer.class);

  private final ListingRepository listingRepository;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "investment.committed", groupId = "marketplace-service")
  @Transactional
  public void onInvestmentCommitted(
      ConsumerRecord<String, InvestmentCommitted> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed investment.committed event {}", eventId);
      return;
    }

    InvestmentCommitted payload = record.value();
    UUID loanId = UUID.fromString(payload.getLoanId().toString());
    listingRepository
        .findById(loanId)
        .ifPresentOrElse(
            listing -> {
              listing.setFundedAmountKobo(listing.getFundedAmountKobo() + payload.getAmountKobo());
              listing.setUpdatedAt(Instant.now());
              listingRepository.save(listing);
            },
            () -> log.info("investment.committed for a loan with no listing row: {}", loanId));

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
