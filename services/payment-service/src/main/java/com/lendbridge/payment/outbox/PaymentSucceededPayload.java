package com.lendbridge.payment.outbox;

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
public class PaymentSucceededPayload {
  private UUID paymentReferenceId;
  private String purpose;
  private UUID walletLedgerAccountId;
  private long amountKobo;
  private String currency;
  private String idempotencyKey;
  private long occurredAtEpochMillis;
}
