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
public class VirtualAccountResponse {
  private UUID id;
  private String bankName;
  private String accountNumber;
  private String providerReference;
  private Instant createdAt;
}
