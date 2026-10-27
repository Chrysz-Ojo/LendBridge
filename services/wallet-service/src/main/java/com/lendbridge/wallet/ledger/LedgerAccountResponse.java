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
public class LedgerAccountResponse {
  private UUID id;
  private String accountType;
  private String currency;
  private String normalBalance;
  private String ownerReference;
}
