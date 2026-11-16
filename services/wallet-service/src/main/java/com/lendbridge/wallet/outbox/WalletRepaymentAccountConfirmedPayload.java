package com.lendbridge.wallet.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletRepaymentAccountConfirmedPayload {
  private String loanId;
  private String instalmentId;
  private boolean success;
  private String borrowerLedgerAccountId;
  private String failureReason;
  private long occurredAtEpochMillis;
}
