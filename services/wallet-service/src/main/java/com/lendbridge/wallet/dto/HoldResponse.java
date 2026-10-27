package com.lendbridge.wallet.dto;

import com.lendbridge.wallet.model.HoldStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HoldResponse {
  private UUID id;
  private long amountKobo;
  private String reference;
  private HoldStatus status;
  private Instant createdAt;
  private Instant releasedAt;
}
