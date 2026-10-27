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
public class PaymentFailedPayload {
  private UUID paymentReferenceId;
  private String purpose;
  private String reason;
  private long occurredAtEpochMillis;
}
