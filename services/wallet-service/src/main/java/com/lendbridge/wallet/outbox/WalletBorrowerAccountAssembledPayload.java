package com.lendbridge.wallet.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletBorrowerAccountAssembledPayload {
  private String loanId;
  private boolean success;
  private String borrowerLedgerAccountId;
  private String failureReason;
  private long occurredAtEpochMillis;
}
