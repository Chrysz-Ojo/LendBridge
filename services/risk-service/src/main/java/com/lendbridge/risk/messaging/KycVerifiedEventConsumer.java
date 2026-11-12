package com.lendbridge.risk.messaging;

import com.lendbridge.kyc.avro.KycVerified;
import com.lendbridge.risk.dto.KycVerifiedEvent;
import com.lendbridge.risk.model.ProcessedEvent;
import com.lendbridge.risk.model.TrustTier;
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
public class KycVerifiedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(KycVerifiedEventConsumer.class);

  private final ProcessedEventRepository processedEventRepository;
  private final RiskAssessmentService riskAssessmentService;

  @KafkaListener(topics = "kyc.verified", groupId = "risk-service")
  @Transactional
  public void onKycVerified(
      ConsumerRecord<String, KycVerified> record, @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed kyc.verified event {}", eventId);
      return;
    }

    KycVerified payload = record.value();
    riskAssessmentService.handleKycVerified(
        KycVerifiedEvent.builder()
            .userId(UUID.fromString(payload.getUserId().toString()))
            .trustTier(TrustTier.valueOf(payload.getTrustTier().toString()))
            .occurredAt(Instant.ofEpochMilli(payload.getOccurredAtEpochMillis()))
            .build());

    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
