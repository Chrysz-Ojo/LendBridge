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

/** A withdrawal. {@code bankAccountId} is opaque — wallet-service's own id, resolved to a
 * decrypted account number via {@code getBankAccountForPayout} only at the moment the provider
 * call is actually made, never stored here. */
@Entity
@Table(name = "payout")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payout {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "wallet_id", nullable = false)
  private UUID walletId;

  @Column(name = "wallet_ledger_account_id", nullable = false)
  private UUID walletLedgerAccountId;

  @Column(name = "bank_account_id", nullable = false)
  private UUID bankAccountId;

  @Column(name = "amount_kobo", nullable = false)
  private long amountKobo;

  @Column(name = "currency", nullable = false)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false)
  private ProviderName provider;

  @Column(name = "provider_reference")
  private String providerReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private PayoutStatus status;

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
