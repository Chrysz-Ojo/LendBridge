package com.lendbridge.loan.outbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanAgreementSignedPayload {
  private UUID loanId;
  private UUID borrowerUserId;
  private long amountKobo;
  private long originationFeeKobo;
  private long occurredAtEpochMillis;
}
