package com.lendbridge.payment.dto;

import com.lendbridge.payment.model.PaymentIntentStatus;
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
public class TopUpResponse {
  private UUID id;
  private long amountKobo;
  private String currency;
  private PaymentIntentStatus status;
  private ProviderName provider;
  private String checkoutUrl;
  private Instant createdAt;
}
