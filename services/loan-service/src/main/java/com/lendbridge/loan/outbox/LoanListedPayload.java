package com.lendbridge.loan.outbox;

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
public class LoanListedPayload {
  private UUID loanId;
  private UUID borrowerUserId;
  private long amountKobo;
  private int interestRateBps;
  private int tenorMonths;
  private String purpose;
  private long occurredAtEpochMillis;
}
