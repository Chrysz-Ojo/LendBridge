package com.lendbridge.ledger.dto;

import com.lendbridge.ledger.model.DebitCredit;
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
public class PostingResponse {
  private UUID id;
  private UUID accountId;
  private DebitCredit direction;
  private long amountKobo;
  private Instant createdAt;
}
