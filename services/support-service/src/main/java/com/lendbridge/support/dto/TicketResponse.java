package com.lendbridge.support.dto;

import com.lendbridge.support.model.TicketCategory;
import com.lendbridge.support.model.TicketStatus;
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
public class TicketResponse {
  private UUID id;
  private UUID userId;
  private String subject;
  private TicketCategory category;
  private String categoryDetail;
  private TicketStatus status;
  private UUID attachmentDocumentId;
  private Instant createdAt;
  private Instant updatedAt;
}
