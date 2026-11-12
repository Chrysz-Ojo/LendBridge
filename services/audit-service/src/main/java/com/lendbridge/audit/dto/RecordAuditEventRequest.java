package com.lendbridge.audit.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Built by {@code AuditEventConsumer} from a raw {@code ConsumerRecord}, so the service layer
 * stays Kafka-agnostic — mirrors every sibling consumer's Avro-payload-to-DTO mapping convention. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordAuditEventRequest {
  private String topic;
  private String aggregateKey;
  private String payload;
  private Instant occurredAt;
  private int kafkaPartition;
  private long kafkaOffset;
}
