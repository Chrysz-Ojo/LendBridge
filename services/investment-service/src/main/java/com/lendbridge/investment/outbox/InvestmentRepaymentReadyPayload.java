package com.lendbridge.investment.outbox;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentRepaymentReadyPayload {
  private String loanId;
  private String instalmentId;
  private String borrowerLedgerAccountId;
  private long totalKobo;
  private List<RepaymentLegPayload> legs;
  private long occurredAtEpochMillis;
}
