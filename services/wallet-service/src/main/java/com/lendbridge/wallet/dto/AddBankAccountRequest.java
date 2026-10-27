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
public class AddBankAccountRequest {

  @NotBlank private String bankCode;
  private String bankName;
  @NotBlank private String accountNumber;
  @NotBlank private String accountHolderName;
}
