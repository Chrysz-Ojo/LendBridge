package com.lendbridge.risk.messaging;

import com.lendbridge.kyc.avro.KycRejected;
import com.lendbridge.risk.dto.KycRejectedEvent;
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
public class KycRejectedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(KycRejectedEventConsumer.class);

  private final ProcessedEventRepository processedEventRepository;
  private final RiskAssessmentService riskAssessmentService;

  @KafkaListener(topics = "kyc.rejected", groupId = "risk-service")
  @Transactional
  public void onKycRejected(
      ConsumerRecord<String, KycRejected> record, @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed kyc.rejected event {}", eventId);
      return;
    }

    KycRejected payload = record.value();
    riskAssessmentService.handleKycRejected(
        KycRejectedEvent.builder()
            .userId(UUID.fromString(payload.getUserId().toString()))
            .reason(payload.getReason().toString())
            .occurredAt(Instant.ofEpochMilli(payload.getOccurredAtEpochMillis()))
            .build());

    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
