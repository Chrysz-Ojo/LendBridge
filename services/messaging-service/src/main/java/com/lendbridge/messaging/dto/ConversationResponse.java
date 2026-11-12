package com.lendbridge.messaging.dto;

import com.lendbridge.messaging.model.ConversationStatus;
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
public class ConversationResponse {
  private UUID id;
  private UUID loanId;
  private UUID borrowerUserId;
  private UUID lenderUserId;
  private ConversationStatus status;
  private Instant createdAt;
}
