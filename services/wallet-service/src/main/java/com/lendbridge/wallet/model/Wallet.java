package com.lendbridge.wallet.model;

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

/**
 * {@code ledgerAccountId} starts null and is filled in lazily on first authenticated access —
 * see {@code WalletServiceImpl.getOrCreateWallet}. There is no service-to-service auth mechanism
 * in this repo, so the Kafka consumer that creates this row cannot itself call ledger-service
 * (no caller JWT to forward); only an end-user-authenticated HTTP request can (ADR: lazy ledger
 * account provisioning).
 */
@Entity
@Table(name = "wallet")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "currency", nullable = false)
  private String currency;

  @Column(name = "ledger_account_id")
  private UUID ledgerAccountId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
