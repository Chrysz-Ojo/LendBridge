package com.lendbridge.payment.provider;

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
public class ProviderChargeResult {
  private String providerReference;
  private ProviderOperationStatus status;
  /** Hosted-checkout flow only (Monnify always; Paystack's authorization_url) — null otherwise. */
  private String checkoutUrl;
  private String rawResponseJson;
}
