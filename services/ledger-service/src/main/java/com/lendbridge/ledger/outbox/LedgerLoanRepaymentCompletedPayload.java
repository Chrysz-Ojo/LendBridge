package com.lendbridge.ledger.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LedgerLoanRepaymentCompletedPayload {
  private String loanId;
  private String instalmentId;
  private boolean success;
  private String reason;
  private long occurredAtEpochMillis;
}
