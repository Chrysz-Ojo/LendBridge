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

/** Schema-only in v1 — no code path creates, activates, or debits a mandate yet. See ADR 0011. */
@Entity
@Table(name = "payment_mandate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMandate {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "wallet_id", nullable = false)
  private UUID walletId;

  @Column(name = "loan_id")
  private UUID loanId;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false)
  private ProviderName provider;

  @Column(name = "provider_mandate_reference")
  private String providerMandateReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private MandateStatus status;

  @Column(name = "authorization_url")
  private String authorizationUrl;

  @Column(name = "token_expires_at")
  private Instant tokenExpiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "activated_at")
  private Instant activatedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;
}
