package com.lendbridge.investment.dto;

import com.lendbridge.investment.model.CommitmentStatus;
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
public class CommitmentResponse {
  private UUID id;
  private UUID loanId;
  private long amountKobo;
  private CommitmentStatus status;
  private Instant createdAt;
  private Instant cancelledAt;
}
