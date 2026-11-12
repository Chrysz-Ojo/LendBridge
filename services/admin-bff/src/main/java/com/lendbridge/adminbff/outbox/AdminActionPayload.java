package com.lendbridge.adminbff.outbox;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Deliberately metadata only — no echo of {@code actionPayload}'s business data onto the event,
 * same privacy precedent as messaging-service's {@code message.sent}. This is what audit-service's
 * generic topic-pattern consumer automatically picks up (ADR 0021/0023). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminActionPayload {
  private UUID approvalId;
  private String actionType;
  private UUID raisedByUserId;
  private UUID decidedByUserId;
  private String status;
  private long occurredAtEpochMillis;
}
