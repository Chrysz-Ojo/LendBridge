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

/** Exactly one of {@code paymentIntentId}/{@code payoutId} is set. 1:many on {@code payoutId} is
 * what lets Monnify's OTP-validate step live as a second row under the same payout
 * (TRANSFER_INITIATE, then TRANSFER_OTP_VALIDATE). */
@Entity
@Table(name = "provider_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderTransaction {

  @Id private UUID id;

  @Column(name = "payment_intent_id")
  private UUID paymentIntentId;

  @Column(name = "payout_id")
  private UUID payoutId;

  @Enumerated(EnumType.STRING)
  @Column(name = "purpose", nullable = false)
  private ProviderTransactionPurpose purpose;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false)
  private ProviderName provider;

  @Column(name = "provider_reference")
  private String providerReference;

  @Column(name = "idempotency_key", unique = true)
  private String idempotencyKey;

  @Column(name = "raw_request_json")
  private String rawRequestJson;

  @Column(name = "raw_response_json")
  private String rawResponseJson;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ProviderTransactionStatus status;

  @Column(name = "reconciliation_run_id")
  private UUID reconciliationRunId;

  @Enumerated(EnumType.STRING)
  @Column(name = "match_status")
  private MatchStatus matchStatus;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
