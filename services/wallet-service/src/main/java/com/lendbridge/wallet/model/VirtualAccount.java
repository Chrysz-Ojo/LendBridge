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

/** Pure storage — Payment Service (not yet built) owns actually provisioning this with a PSP;
 * wallet-service only records what it's told. */
@Entity
@Table(name = "virtual_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VirtualAccount {

  @Id private UUID id;

  @Column(name = "wallet_id", nullable = false)
  private UUID walletId;

  @Column(name = "bank_name", nullable = false)
  private String bankName;

  @Column(name = "account_number", nullable = false)
  private String accountNumber;

  @Column(name = "provider_reference")
  private String providerReference;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
