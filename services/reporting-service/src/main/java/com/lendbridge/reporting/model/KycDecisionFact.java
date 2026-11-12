package com.lendbridge.reporting.model;

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

/** One row per {@code kyc.verified}/{@code kyc.rejected} event — backs the KYC-approval-rate
 * report. {@code trustTier} is only set on {@code VERIFIED} ({@code kyc.rejected} carries no
 * tier). */
@Entity
@Table(name = "kyc_decision_fact")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycDecisionFact {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "decision", nullable = false)
  private KycDecisionType decision;

  @Column(name = "trust_tier")
  private String trustTier;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;
}
