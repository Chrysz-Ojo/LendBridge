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
public class FaqResponse {
  private UUID id;
  private String category;
  private String question;
  private String answer;
  private boolean published;
  private Instant createdAt;
  private Instant updatedAt;
}
