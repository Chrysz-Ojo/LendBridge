package com.lendbridge.payment.wallet;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Mirrors wallet-service's own {@code WalletResponse} — no cross-service jar dependency, same
 * rule as copying another service's Avro schemas verbatim. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletSummaryResponse {
  private UUID id;
  private UUID ledgerAccountId;
  private String currency;
  private long ledgerBalanceKobo;
  private long heldKobo;
  private long availableKobo;
  private boolean stale;
}
