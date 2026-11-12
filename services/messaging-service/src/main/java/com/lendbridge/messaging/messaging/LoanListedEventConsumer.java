package com.lendbridge.messaging.messaging;

import com.lendbridge.loan.avro.LoanListed;
import com.lendbridge.messaging.model.BorrowerLoanProjection;
import com.lendbridge.messaging.model.ProcessedEvent;
import com.lendbridge.messaging.repository.BorrowerLoanProjectionRepository;
import com.lendbridge.messaging.repository.ProcessedEventRepository;
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

/** Builds the {@code BorrowerLoanProjection} read model this service needs to know a loan's
 * borrower — marketplace-service's own listing API deliberately omits it (ADR 0016), so this is
 * sourced directly from the event, same idiom as every other cross-service read model in this
 * repo. Idempotent via {@code processed_event}. */
@Component
@RequiredArgsConstructor
public class LoanListedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(LoanListedEventConsumer.class);

  private final ProcessedEventRepository processedEventRepository;
  private final BorrowerLoanProjectionRepository borrowerLoanProjectionRepository;

  @KafkaListener(topics = "loan.listed", groupId = "messaging-service")
  @Transactional
  public void onLoanListed(
      ConsumerRecord<String, LoanListed> record, @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed loan.listed event {}", eventId);
      return;
    }

    LoanListed payload = record.value();
    UUID loanId = UUID.fromString(payload.getLoanId().toString());
    UUID borrowerUserId = UUID.fromString(payload.getBorrowerUserId().toString());

    borrowerLoanProjectionRepository.save(
        BorrowerLoanProjection.builder()
            .loanId(loanId)
            .borrowerUserId(borrowerUserId)
            .updatedAt(Instant.now())
            .build());

    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
