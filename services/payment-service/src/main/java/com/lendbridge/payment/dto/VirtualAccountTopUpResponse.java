package com.lendbridge.payment.dto;

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
public class VirtualAccountTopUpResponse {
  private String accountNumber;
  private String bankName;
  private ProviderName provider;
  private String providerReference;
}
