package com.lendbridge.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per Kafka record consumed, across every topic in the system (see
 * {@code AuditEventConsumer}) — append-only, no update or delete path anywhere in this service.
 * {@code payload} is the record's Avro {@code GenericRecord} rendered via its own
 * {@code toString()} (Avro's {@code GenericData} JSON rendering), not a hand-rolled encoder.
 */
@Entity
@Table(name = "audit_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditRecord {

  @Id private UUID id;

  @Column(name = "topic", nullable = false)
  private String topic;

  @Column(name = "aggregate_key")
  private String aggregateKey;

  @Column(name = "payload", nullable = false)
  private String payload;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;

  @Column(name = "kafka_partition", nullable = false)
  private int kafkaPartition;

  @Column(name = "kafka_offset", nullable = false)
  private long kafkaOffset;
}
