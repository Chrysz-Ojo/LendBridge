package com.lendbridge.wallet.model;

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
 * A reservation against a wallet's available balance — never a ledger posting. Only when a loan
 * is funded (Loan's funding saga, not yet built) does a hold convert into a real ledger debit.
 * {@code idempotencyKey} exists because creating a hold reserves real user funds even though
 * nothing is posted to the ledger: an un-deduplicated retry would double-reserve a lender's
 * available balance, a real correctness bug despite no money actually moving.
 */
@Entity
@Table(name = "hold")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hold {

  @Id private UUID id;

  @Column(name = "wallet_id", nullable = false)
  private UUID walletId;

  @Column(name = "amount_kobo", nullable = false)
  private long amountKobo;

  @Column(name = "reference")
  private String reference;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private HoldStatus status;

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "released_at")
  private Instant releasedAt;
}
