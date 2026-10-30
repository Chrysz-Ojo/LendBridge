package com.lendbridge.investment.messaging;

import com.lendbridge.investment.model.ProcessedEvent;
import com.lendbridge.investment.repository.LoanListingRepository;
import com.lendbridge.investment.repository.ProcessedEventRepository;
import com.lendbridge.loan.avro.LoanDelisted;
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

/** Marks a {@code LoanListing} inactive so no further commitments can be taken against it — a
 * loan this service never saw {@code loan.listed} for (cancelled while Draft/Under review) has no
 * row to update, not an error, just a no-op (same reasoning as marketplace-service's own
 * consumer of this topic). Existing commitments against the now-delisted loan are left alone —
 * Phase 1 doesn't auto-cancel them; that's a funding-saga-era concern. */
@Component
@RequiredArgsConstructor
public class LoanDelistedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(LoanDelistedEventConsumer.class);

  private final LoanListingRepository loanListingRepository;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "loan.delisted", groupId = "investment-service")
  @Transactional
  public void onLoanDelisted(
      ConsumerRecord<String, LoanDelisted> record, @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed loan.delisted event {}", eventId);
      return;
    }

    UUID loanId = UUID.fromString(record.value().getLoanId().toString());
    loanListingRepository
        .findById(loanId)
        .ifPresentOrElse(
            listing -> {
              listing.setActive(false);
              loanListingRepository.save(listing);
            },
            () -> log.info("loan.delisted for a loan this service never saw loan.listed for: {}", loanId));

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
