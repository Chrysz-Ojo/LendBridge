package com.lendbridge.adminbff.dto;

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
public class AdminTicketResponse {
  private UUID id;
  private UUID userId;
  private String subject;
  private String category;
  private String categoryDetail;
  private String status;
  private UUID attachmentDocumentId;
  private Instant createdAt;
  private Instant updatedAt;
}
