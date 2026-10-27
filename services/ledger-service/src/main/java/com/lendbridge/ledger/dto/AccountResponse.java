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
public class AccountResponse {
  private UUID id;
  private String accountType;
  private String currency;
  private DebitCredit normalBalance;
  private String ownerReference;
  private Instant createdAt;
}
