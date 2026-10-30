package com.lendbridge.loan.model;

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
 * Owned entirely by this service — {@code borrowerUserId} is a plain column referencing
 * authentication-service's user id, never a cross-service foreign key ("each service owns its
 * database; never read another service's tables"). A partial unique index
 * (see {@code 001-loan-application.yaml}) enforces at most one open request per borrower with
 * {@code status IN (DRAFT, UNDER_REVIEW, LISTED)} (PRD E6-S5), same technique as kyc-service's
 * open-verification-case index.
 */
@Entity
@Table(name = "loan_application")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanApplication {

  @Id private UUID id;

  @Column(name = "borrower_user_id", nullable = false)
  private UUID borrowerUserId;

  @Column(name = "amount_kobo", nullable = false)
  private long amountKobo;

  @Column(name = "tenor_months", nullable = false)
  private int tenorMonths;

  @Enumerated(EnumType.STRING)
  @Column(name = "purpose", nullable = false)
  private LoanPurpose purpose;

  @Column(name = "purpose_detail")
  private String purposeDetail;

  @Column(name = "interest_rate_bps", nullable = false)
  private int interestRateBps;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private LoanStatus status;

  @Column(name = "review_required", nullable = false)
  private boolean reviewRequired;

  @Column(name = "decided_by_user_id")
  private UUID decidedByUserId;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "rejection_reason")
  private String rejectionReason;

  @Column(name = "agreement_document_id")
  private UUID agreementDocumentId;

  @Column(name = "listed_at")
  private Instant listedAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
