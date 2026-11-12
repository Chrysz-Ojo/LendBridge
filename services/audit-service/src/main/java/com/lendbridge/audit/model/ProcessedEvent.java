package com.lendbridge.audit.model;

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

/**
 * Idempotency ledger for {@code AuditEventConsumer}'s single topic-pattern listener. Keyed on
 * {@code "<topic>:<ce_id-or-offset>"} rather than a bare event id, since one listener spans every
 * unrelated topic in the system and a bare {@code ce_id} alone isn't guaranteed unique across them.
 */
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
