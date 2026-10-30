package com.lendbridge.marketplace.messaging;

import com.lendbridge.loan.avro.LoanDelisted;
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

/** Soft-removes a listing when its loan is cancelled — kept as a {@code DELISTED} row, not
 * deleted, as a record this consumer saw the cancellation (same reasoning as ledger-service's
 * {@code PaymentFailedEventConsumer}). A loan this service never saw {@code loan.listed} for
 * (cancelled while Draft/Under review) has no row to update — not an error, just a no-op. */
@Component
@RequiredArgsConstructor
public class LoanDelistedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(LoanDelistedEventConsumer.class);

  private final ListingRepository listingRepository;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "loan.delisted", groupId = "marketplace-service")
  @Transactional
  public void onLoanDelisted(
      ConsumerRecord<String, LoanDelisted> record, @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed loan.delisted event {}", eventId);
      return;
    }

    UUID loanId = UUID.fromString(record.value().getLoanId().toString());
    listingRepository
        .findById(loanId)
        .ifPresentOrElse(
            listing -> {
              listing.setStatus(ListingStatus.DELISTED);
              listing.setUpdatedAt(Instant.now());
              listingRepository.save(listing);
            },
            () -> log.info("loan.delisted for a loan with no listing row (never reached Listed): {}", loanId));

    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
