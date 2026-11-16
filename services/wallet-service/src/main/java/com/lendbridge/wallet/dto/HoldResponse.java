package com.lendbridge.wallet.dto;

import com.lendbridge.wallet.model.HoldStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HoldResponse {
  private UUID id;
  private long amountKobo;
  private String reference;
  private HoldStatus status;
  private Instant createdAt;
  private Instant releasedAt;

  /** Only populated by {@code createHold}'s response, not by the mapper (it's the wallet's own
   * ledger account, not a column on {@code Hold} itself) — lets investment-service store it on its
   * own {@code Commitment} row at commitment-creation time with no extra lookup, since the
   * underlying ledger account is already guaranteed provisioned by that point (the balance check
   * {@code createHold} just ran provisions it lazily if needed). */
  private UUID walletLedgerAccountId;
}
