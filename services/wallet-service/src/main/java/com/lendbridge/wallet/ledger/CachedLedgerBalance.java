package com.lendbridge.wallet.ledger;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JSON shape stored in Redis under {@code wallet:ledger-balance:<ledgerAccountId>}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CachedLedgerBalance {
  private long balanceKobo;
  private String currency;
  private long cachedAtEpochMillis;
}
