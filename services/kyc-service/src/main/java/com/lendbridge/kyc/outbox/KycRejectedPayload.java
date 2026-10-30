package com.lendbridge.kyc.outbox;

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
public class KycRejectedPayload {
  private UUID verificationCaseId;
  private UUID userId;
  private String reason;
  private long occurredAtEpochMillis;
}
