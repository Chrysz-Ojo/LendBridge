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
public class KycVerificationStatusResponse {
  private UUID id;
  private String status;
  private String trustTier;
  private boolean reviewRequired;
  private String rejectionReason;
  private String needsMoreInfoReason;
  private Instant createdAt;
  private Instant updatedAt;
}
