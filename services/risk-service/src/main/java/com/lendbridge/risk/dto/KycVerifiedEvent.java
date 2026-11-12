package com.lendbridge.risk.dto;

import com.lendbridge.risk.model.TrustTier;
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
public class KycVerifiedEvent {
  private UUID userId;
  private TrustTier trustTier;
  private Instant occurredAt;
}
