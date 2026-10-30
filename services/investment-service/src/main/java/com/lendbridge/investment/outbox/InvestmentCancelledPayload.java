package com.lendbridge.investment.outbox;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentCancelledPayload {
  private UUID commitmentId;
  private UUID loanId;
  private long amountKobo;
  private long occurredAtEpochMillis;
}
