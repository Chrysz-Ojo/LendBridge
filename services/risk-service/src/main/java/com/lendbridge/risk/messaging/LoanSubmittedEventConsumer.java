package com.lendbridge.risk.messaging;

import com.lendbridge.loan.avro.LoanSubmitted;
import com.lendbridge.risk.dto.LoanSubmittedEvent;
import com.lendbridge.risk.model.ProcessedEvent;
import com.lendbridge.risk.repository.ProcessedEventRepository;
import com.lendbridge.risk.service.RiskAssessmentService;
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

/** Idempotent against redelivery via {@code processed_event}, same pattern as
 * notification-service's own event consumers. */
@Component
@RequiredArgsConstructor
public class LoanSubmittedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(LoanSubmittedEventConsumer.class);

  private final ProcessedEventRepository processedEventRepository;
  private final RiskAssessmentService riskAssessmentService;

  @KafkaListener(topics = "loan.submitted", groupId = "risk-service")
  @Transactional
  public void onLoanSubmitted(
      ConsumerRecord<String, LoanSubmitted> record, @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed loan.submitted event {}", eventId);
      return;
    }

    LoanSubmitted payload = record.value();
    riskAssessmentService.handleLoanSubmitted(
        LoanSubmittedEvent.builder()
            .loanId(UUID.fromString(payload.getLoanId().toString()))
            .borrowerUserId(UUID.fromString(payload.getBorrowerUserId().toString()))
            .amountKobo(payload.getAmountKobo())
            .tenorMonths(payload.getTenorMonths())
            .interestRateBps(payload.getInterestRateBps())
            .occurredAt(Instant.ofEpochMilli(payload.getOccurredAtEpochMillis()))
            .build());

    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
