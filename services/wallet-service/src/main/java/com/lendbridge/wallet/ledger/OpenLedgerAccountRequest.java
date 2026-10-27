package com.lendbridge.wallet.ledger;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirrors ledger-service's own {@code OpenAccountRequest} — no cross-service jar dependency, same
 * rule as copying its Avro schemas verbatim.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OpenLedgerAccountRequest {
  private String accountType;
  private String currency;
  private String normalBalance;
  private String ownerReference;
}
