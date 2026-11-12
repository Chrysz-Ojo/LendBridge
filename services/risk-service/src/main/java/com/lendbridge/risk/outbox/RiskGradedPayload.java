package com.lendbridge.risk.outbox;

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
public class RiskGradedPayload {
  private UUID loanId;
  private UUID borrowerUserId;
  private String grade;
  private String modelVersion;
  private long occurredAtEpochMillis;
}
