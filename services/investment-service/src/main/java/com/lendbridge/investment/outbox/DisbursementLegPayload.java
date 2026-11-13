package com.lendbridge.investment.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DisbursementLegPayload {
  private String lenderLedgerAccountId;
  private long amountKobo;
}
