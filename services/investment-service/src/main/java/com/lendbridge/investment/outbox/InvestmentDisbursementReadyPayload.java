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
public class InvestmentDisbursementReadyPayload {
  private String loanId;
  private String borrowerLedgerAccountId;
  private long originationFeeKobo;
  private long totalKobo;
  private List<DisbursementLegPayload> legs;
  private long occurredAtEpochMillis;
}
