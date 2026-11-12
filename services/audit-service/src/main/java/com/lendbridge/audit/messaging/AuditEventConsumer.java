package com.lendbridge.audit.messaging;

import com.lendbridge.audit.dto.RecordAuditEventRequest;
import com.lendbridge.audit.model.ProcessedEvent;
import com.lendbridge.audit.repository.ProcessedEventRepository;
import com.lendbridge.audit.service.AuditRecordService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A single listener subscribed by topic pattern, not one typed consumer per topic like every
 * sibling service — see ADR 0021 for why. Matches every existing domain topic's {@code noun.verb}
 * naming convention ({@code user.registered}, {@code wallet.hold.created}, ...) and automatically
 * picks up any future service's new topics with zero code change here; Kafka's own internal topics
 * (e.g. {@code __consumer_offsets}) don't match and are never subscribed.
 *
 * <p>Consumes generic Avro ({@code specific.avro.reader: false} in {@code application.yml}) so
 * this service needs no copy of any other service's {@code .avsc} schemas.
 */
@Component
@RequiredArgsConstructor
public class AuditEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);
  private static final String OCCURRED_AT_FIELD = "occurredAtEpochMillis";

  private final ProcessedEventRepository processedEventRepository;
  private final AuditRecordService auditRecordService;

  @KafkaListener(topicPattern = "^[a-z]+(_[a-z]+)*\\.[a-z_.]+$", groupId = "audit-service")
  @Transactional
  public void onEvent(
      ConsumerRecord<String, GenericRecord> record, @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String ceId = ceIdHeader != null ? new String(ceIdHeader, StandardCharsets.UTF_8) : null;
    String eventId = record.topic() + ":" + (ceId != null ? ceId : String.valueOf(record.offset()));
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed event {}", eventId);
      return;
    }

    GenericRecord value = record.value();
    auditRecordService.record(
        RecordAuditEventRequest.builder()
            .topic(record.topic())
            .aggregateKey(record.key())
            // Avro's own GenericData JSON rendering, not a hand-rolled encoder.
            .payload(value != null ? value.toString() : null)
            .occurredAt(extractOccurredAt(value, record.timestamp()))
            .kafkaPartition(record.partition())
            .kafkaOffset(record.offset())
            .build());

    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }

  /** Best-effort: every existing producer's payload carries {@code occurredAtEpochMillis}, but
   * this consumer has no schema-specific knowledge, so it checks the field exists before reading
   * it rather than assuming every current and future topic will have it — falling back to the
   * Kafka record's own broker-assigned timestamp. */
  private static Instant extractOccurredAt(GenericRecord value, long kafkaTimestampMillis) {
    if (value != null && value.getSchema().getField(OCCURRED_AT_FIELD) != null) {
      Object raw = value.get(OCCURRED_AT_FIELD);
      if (raw instanceof Long epochMillis) {
        return Instant.ofEpochMilli(epochMillis);
      }
    }
    return Instant.ofEpochMilli(kafkaTimestampMillis);
  }
}
