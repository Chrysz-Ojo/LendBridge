package com.lendbridge.payment.provider;

import com.lendbridge.payment.model.ProviderName;
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
public class ProviderWebhookEvent {
  private ProviderName provider;
  private ProviderWebhookEventType eventType;
  private String providerReference;
  private long amountKobo;
  private String rawPayloadJson;
}
