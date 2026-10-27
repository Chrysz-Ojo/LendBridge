package com.lendbridge.wallet.dto;

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
public class WalletResponse {
  private UUID id;
  private UUID ledgerAccountId;
  private String currency;
  private long ledgerBalanceKobo;
  private long heldKobo;
  private long availableKobo;
  private boolean stale;
  private Instant staleAsOf;
}
