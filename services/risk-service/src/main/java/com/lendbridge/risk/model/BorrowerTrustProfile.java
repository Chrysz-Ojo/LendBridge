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
 * Per-user projection built from {@code kyc.verified}/{@code kyc.rejected} — risk-service never
 * reads kyc-service's own tables ("each service owns its database"), only the trust tier carried
 * on the event. {@code sourceOccurredAt} guards against out-of-order redelivery: an incoming event
 * older than what's stored here is a no-op (see the approved plan's processing logic).
 */
@Entity
@Table(name = "borrower_trust_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BorrowerTrustProfile {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private TrustProfileStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "trust_tier")
  private TrustTier trustTier;

  @Column(name = "reason")
  private String reason;

  @Column(name = "source_occurred_at", nullable = false)
  private Instant sourceOccurredAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
