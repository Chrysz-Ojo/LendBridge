package com.lendbridge.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterVirtualAccountRequest {

  @NotBlank private String bankName;
  @NotBlank private String accountNumber;
  private String providerReference;
}
