package com.lendbridge.reporting.messaging;

import com.lendbridge.reporting.model.ProcessedEvent;
import com.lendbridge.reporting.model.RiskGradeFact;
import com.lendbridge.reporting.repository.ProcessedEventRepository;
import com.lendbridge.reporting.repository.RiskGradeFactRepository;
import com.lendbridge.risk.avro.RiskGraded;
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
public class RiskGradedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(RiskGradedEventConsumer.class);

  private final ProcessedEventRepository processedEventRepository;
  private final RiskGradeFactRepository riskGradeFactRepository;

  @KafkaListener(topics = "risk.graded", groupId = "reporting-service")
  @Transactional
  public void onRiskGraded(
      ConsumerRecord<String, RiskGraded> record, @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String ceId = ceIdHeader != null ? new String(ceIdHeader) : null;
    String eventId = record.topic() + ":" + (ceId != null ? ceId : String.valueOf(record.offset()));
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed risk.graded event {}", eventId);
      return;
    }

    RiskGraded payload = record.value();
    riskGradeFactRepository.save(
        RiskGradeFact.builder()
            .id(UuidV7.generate())
            .loanId(UUID.fromString(payload.getLoanId().toString()))
            .grade(payload.getGrade().toString())
            .modelVersion(payload.getModelVersion().toString())
            .occurredAt(Instant.ofEpochMilli(payload.getOccurredAtEpochMillis()))
            .build());

    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
