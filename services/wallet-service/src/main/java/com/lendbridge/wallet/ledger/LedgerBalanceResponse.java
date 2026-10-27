package com.lendbridge.wallet.ledger;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LedgerBalanceResponse {
  private UUID accountId;
  private String currency;
  private long balanceKobo;
}
