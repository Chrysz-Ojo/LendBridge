package com.lendbridge.risk.model;

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

/**
 * One row per loan, keyed on the loan's own id (same idiom as marketplace-service's
 * {@code Listing}). The {@code amountKobo}/{@code tenorMonths}/{@code interestRateBps} columns are
 * a snapshot captured at {@code loan.submitted} time, since that event is never replayable later
 * (no compaction/replay guarantee — see the approved plan's data model).
 */
@Entity
@Table(name = "risk_assessment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskAssessment {

  @Id private UUID loanId;

  @Column(name = "borrower_user_id", nullable = false)
  private UUID borrowerUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private RiskAssessmentStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "grade")
  private RiskGrade grade;

  @Column(name = "model_version")
  private String modelVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "trust_tier_used")
  private TrustTier trustTierUsed;

  @Column(name = "amount_kobo", nullable = false)
  private long amountKobo;

  @Column(name = "tenor_months", nullable = false)
  private int tenorMonths;

  @Column(name = "interest_rate_bps", nullable = false)
  private int interestRateBps;

  @Column(name = "kyc_rejection_reason")
  private String kycRejectionReason;

  @Column(name = "graded_at")
  private Instant gradedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
