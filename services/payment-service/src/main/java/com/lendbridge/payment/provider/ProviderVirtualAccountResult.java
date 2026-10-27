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
public class ProviderVirtualAccountResult {
  private String accountNumber;
  private String bankName;
  private String providerReference;
}
