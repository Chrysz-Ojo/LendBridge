package com.lendbridge.reporting.model;

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

/** One row per {@code risk.graded} event — backs the risk-grade-distribution report, the stand-in
 * for "defaults by grade" until real default data exists (see ADR 0022). {@code grade} is stored
 * as a plain string, a local copy of the value carried on the event, not a dependency on
 * risk-service's own {@code RiskGrade} enum (same idiom risk-service itself used for
 * {@code TrustTier}). */
@Entity
@Table(name = "risk_grade_fact")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskGradeFact {

  @Id private UUID id;

  @Column(name = "loan_id", nullable = false)
  private UUID loanId;

  @Column(name = "grade", nullable = false)
  private String grade;

  @Column(name = "model_version", nullable = false)
  private String modelVersion;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;
}
