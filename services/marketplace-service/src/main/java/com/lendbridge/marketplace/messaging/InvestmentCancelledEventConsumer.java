package com.lendbridge.marketplace.messaging;

import com.lendbridge.investment.avro.InvestmentCancelled;
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

/** Subtracts a cancelled commitment back off {@code Listing.fundedAmountKobo} — the mirror image
 * of {@link InvestmentCommittedEventConsumer}. */
@Component
@RequiredArgsConstructor
public class InvestmentCancelledEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(InvestmentCancelledEventConsumer.class);

  private final ListingRepository listingRepository;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "investment.cancelled", groupId = "marketplace-service")
  @Transactional
  public void onInvestmentCancelled(
      ConsumerRecord<String, InvestmentCancelled> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed investment.cancelled event {}", eventId);
      return;
    }

    InvestmentCancelled payload = record.value();
    UUID loanId = UUID.fromString(payload.getLoanId().toString());
    listingRepository
        .findById(loanId)
        .ifPresentOrElse(
            listing -> {
              listing.setFundedAmountKobo(Math.max(0, listing.getFundedAmountKobo() - payload.getAmountKobo()));
              listing.setUpdatedAt(Instant.now());
              listingRepository.save(listing);
            },
            () -> log.info("investment.cancelled for a loan with no listing row: {}", loanId));

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
