package com.lendbridge.messaging.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
  private UUID id;
  private UUID conversationId;
  private UUID senderUserId;
  private String body;
  private Instant createdAt;
}
