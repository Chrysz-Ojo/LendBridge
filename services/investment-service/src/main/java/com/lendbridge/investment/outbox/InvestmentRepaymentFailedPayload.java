package com.lendbridge.investment.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentRepaymentFailedPayload {
  private String loanId;
  private String instalmentId;
  private String reason;
  private long occurredAtEpochMillis;
}
