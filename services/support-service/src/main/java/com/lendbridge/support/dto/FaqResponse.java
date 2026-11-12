package com.lendbridge.support.dto;

import com.lendbridge.support.model.TicketCategory;
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
public class FaqResponse {
  private UUID id;
  private TicketCategory category;
  private String question;
  private String answer;
  private boolean published;
  private Instant createdAt;
  private Instant updatedAt;
}
