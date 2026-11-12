package com.lendbridge.messaging.outbox;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSentPayload {
  private UUID conversationId;
  private UUID messageId;
  private UUID senderUserId;
  private UUID recipientUserId;
  private long occurredAtEpochMillis;
}
