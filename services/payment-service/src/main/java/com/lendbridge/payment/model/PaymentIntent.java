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

/**
 * A top-up or repayment attempt. {@code walletLedgerAccountId} is fetched from wallet-service
 * (forwarding the end-user's own JWT) and cached here at creation time — the webhook handler that
 * later confirms success has no caller JWT at all, so it can never call wallet-service itself
 * (same "no service-to-service auth" wall as ADR 0008, solved the same way).
 */
@Entity
@Table(name = "payment_intent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentIntent {

  @Id private UUID id;

  @Column(name = "wallet_id", nullable = false)
  private UUID walletId;

  @Column(name = "wallet_ledger_account_id", nullable = false)
  private UUID walletLedgerAccountId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "purpose", nullable = false)
  private PaymentPurpose purpose;

  @Enumerated(EnumType.STRING)
  @Column(name = "repayment_method")
  private RepaymentMethod repaymentMethod;

  @Column(name = "loan_id")
  private UUID loanId;

  @Column(name = "amount_kobo", nullable = false)
  private long amountKobo;

  @Column(name = "currency", nullable = false)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private PaymentIntentStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false)
  private ProviderName provider;

  @Column(name = "provider_reference")
  private String providerReference;

  @Column(name = "checkout_url")
  private String checkoutUrl;

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
