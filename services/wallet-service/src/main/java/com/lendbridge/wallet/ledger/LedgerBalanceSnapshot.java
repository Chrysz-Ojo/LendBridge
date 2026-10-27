package com.lendbridge.wallet.ledger;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code stale=true} means this came from the Redis cache because Ledger was unreachable, not
 * from a live read. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LedgerBalanceSnapshot {
  private long balanceKobo;
  private String currency;
  private boolean stale;
  private Instant staleAsOf;
}
