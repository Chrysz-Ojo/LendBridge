package com.lendbridge.ledger.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tracks Kafka event ids already handled, so a consumer redelivery (at-least-once) doesn't
 * re-apply the same event — CLAUDE.md: "consumers must be idempotent". */
@Entity
@Table(name = "processed_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

  @Id
  @Column(name = "event_id")
  private String eventId;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;
}
