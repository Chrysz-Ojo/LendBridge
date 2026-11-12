package com.lendbridge.reporting.model;

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

/** Idempotency ledger shared by every consumer in this service — CLAUDE.md: "consumers must be
 * idempotent". {@code eventId} is {@code topic:ce_id_or_offset}, same composite-key idiom
 * audit-service established, since several unrelated consumers share this one table. */
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
