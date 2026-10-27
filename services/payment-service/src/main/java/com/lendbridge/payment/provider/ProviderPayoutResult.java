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
public class ProviderPayoutResult {
  private String providerReference;
  private ProviderOperationStatus status;
  private String rawResponseJson;
}
