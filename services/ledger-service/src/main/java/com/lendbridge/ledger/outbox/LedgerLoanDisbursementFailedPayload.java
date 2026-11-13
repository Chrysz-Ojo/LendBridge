package com.lendbridge.ledger.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LedgerLoanDisbursementFailedPayload {
  private String loanId;
  private String reason;
  private long occurredAtEpochMillis;
}
