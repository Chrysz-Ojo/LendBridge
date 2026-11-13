package com.lendbridge.loan.messaging;

import com.lendbridge.investment.avro.InvestmentCancelled;
import com.lendbridge.loan.model.ProcessedEvent;
import com.lendbridge.loan.repository.ProcessedEventRepository;
import com.lendbridge.loan.service.LoanService;
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

/** Funding saga Stage 1's inverse: a cancelled commitment subtracts back off the running funded
 * total, potentially reverting {@code FUNDED -> FUNDING}. */
@Component
@RequiredArgsConstructor
public class InvestmentCancelledEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(InvestmentCancelledEventConsumer.class);

  private final LoanService loanService;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "investment.cancelled", groupId = "loan-service")
  @Transactional
  public void onCancelled(
      ConsumerRecord<String, InvestmentCancelled> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed investment.cancelled event {}", eventId);
      return;
    }

    InvestmentCancelled payload = record.value();
    UUID loanId = UUID.fromString(payload.getLoanId().toString());
    loanService.recordCommitmentCancelled(loanId, payload.getAmountKobo());

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
