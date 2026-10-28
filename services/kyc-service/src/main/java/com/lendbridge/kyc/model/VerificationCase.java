package com.lendbridge.kyc.model;

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
 * Owned entirely by this service — {@code userId} is a plain column referencing
 * authentication-service's user id, never a cross-service foreign key ("each service owns its
 * database; never read another service's tables"). A partial unique index
 * (see {@code 001-verification-case.yaml}) enforces at most one row per user with
 * {@code status IN (PENDING, NEEDS_MORE_INFO)} — the "open case" {@code IdempotencyOperations}
 * keys off of (see the approved plan's design decisions).
 *
 * <p>{@code documentSnapshot} is a JSON blob capturing the id/type/scan-status/checksum of every
 * document used by this case's checks, taken at check time. Because there is no service-to-service
 * (M2M) auth yet, {@code DocumentGateway} can only fetch a document under the *caller's own* JWT —
 * a KYC officer's review queue cannot live-re-fetch a borrower's documents, so this snapshot is
 * what the review queue actually displays (known gap, see ADR 0014 and the approved plan's
 * design decisions).
 */
@Entity
@Table(name = "verification_case")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationCase {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private VerificationStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "trust_tier")
  private TrustTier trustTier;

  @Column(name = "review_required", nullable = false)
  private boolean reviewRequired;

  @Column(name = "rejection_reason")
  private String rejectionReason;

  @Column(name = "needs_more_info_reason")
  private String needsMoreInfoReason;

  @Column(name = "id_card_document_id", nullable = false)
  private UUID idCardDocumentId;

  @Column(name = "selfie_document_id", nullable = false)
  private UUID selfieDocumentId;

  @Column(name = "bank_statement_document_id")
  private UUID bankStatementDocumentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "bank_statement_outcome")
  private CheckOutcome bankStatementOutcome;

  @Column(name = "document_snapshot", columnDefinition = "text")
  private String documentSnapshot;

  @Column(name = "decided_by_user_id")
  private UUID decidedByUserId;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
