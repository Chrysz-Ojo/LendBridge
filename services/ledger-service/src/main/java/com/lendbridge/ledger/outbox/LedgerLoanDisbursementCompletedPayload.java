package com.lendbridge.ledger.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LedgerLoanDisbursementCompletedPayload {
  private String loanId;
  private long totalKobo;
  private long occurredAtEpochMillis;
}
