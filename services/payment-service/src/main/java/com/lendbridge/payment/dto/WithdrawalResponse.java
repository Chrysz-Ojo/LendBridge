package com.lendbridge.payment.dto;

import com.lendbridge.payment.model.PayoutStatus;
import com.lendbridge.payment.model.ProviderName;
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
public class WithdrawalResponse {
  private UUID id;
  private long amountKobo;
  private String currency;
  private PayoutStatus status;
  private ProviderName provider;
  private Instant createdAt;
}
