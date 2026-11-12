package com.lendbridge.reporting.messaging;

import com.lendbridge.kyc.avro.KycRejected;
import com.lendbridge.kyc.avro.KycVerified;
import com.lendbridge.reporting.model.KycDecisionFact;
import com.lendbridge.reporting.model.KycDecisionType;
import com.lendbridge.reporting.model.ProcessedEvent;
import com.lendbridge.reporting.repository.KycDecisionFactRepository;
import com.lendbridge.reporting.repository.ProcessedEventRepository;
import com.lendbridge.starter.crypto.UuidV7;
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

@Component
@RequiredArgsConstructor
public class KycDecisionEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(KycDecisionEventConsumer.class);

  private final ProcessedEventRepository processedEventRepository;
  private final KycDecisionFactRepository kycDecisionFactRepository;

  @KafkaListener(topics = "kyc.verified", groupId = "reporting-service")
  @Transactional
  public void onKycVerified(
      ConsumerRecord<String, KycVerified> record, @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = idFor(record, ceIdHeader);
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed kyc.verified event {}", eventId);
      return;
    }
    KycVerified payload = record.value();
    kycDecisionFactRepository.save(
        KycDecisionFact.builder()
            .id(UuidV7.generate())
            .userId(UUID.fromString(payload.getUserId().toString()))
            .decision(KycDecisionType.VERIFIED)
            .trustTier(payload.getTrustTier().toString())
            .occurredAt(Instant.ofEpochMilli(payload.getOccurredAtEpochMillis()))
            .build());
    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }

  @KafkaListener(topics = "kyc.rejected", groupId = "reporting-service")
  @Transactional
  public void onKycRejected(
      ConsumerRecord<String, KycRejected> record, @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = idFor(record, ceIdHeader);
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed kyc.rejected event {}", eventId);
      return;
    }
    KycRejected payload = record.value();
    kycDecisionFactRepository.save(
        KycDecisionFact.builder()
            .id(UuidV7.generate())
            .userId(UUID.fromString(payload.getUserId().toString()))
            .decision(KycDecisionType.REJECTED)
            .trustTier(null)
            .occurredAt(Instant.ofEpochMilli(payload.getOccurredAtEpochMillis()))
            .build());
    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }

  private static String idFor(ConsumerRecord<String, ?> record, byte[] ceIdHeader) {
    String ceId = ceIdHeader != null ? new String(ceIdHeader) : null;
    return record.topic() + ":" + (ceId != null ? ceId : String.valueOf(record.offset()));
  }
}
