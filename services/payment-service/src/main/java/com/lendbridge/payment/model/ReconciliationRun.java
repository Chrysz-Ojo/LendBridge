package com.lendbridge.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** On-demand only in v1 (ADR 0012) — nothing schedules this automatically. */
@Entity
@Table(name = "reconciliation_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationRun {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false)
  private ProviderName provider;

  @Column(name = "period_start", nullable = false)
  private Instant periodStart;

  @Column(name = "period_end", nullable = false)
  private Instant periodEnd;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ReconciliationStatus status;

  @Column(name = "total_provider_records", nullable = false)
  private int totalProviderRecords;

  @Column(name = "total_matched", nullable = false)
  private int totalMatched;

  @Column(name = "total_breaks", nullable = false)
  private int totalBreaks;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;
}
