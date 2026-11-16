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
public class InvestmentDisbursementCompensatePayload {
  private String loanId;
  private List<String> holdIds;
  private long occurredAtEpochMillis;
}
