package com.lendbridge.wallet.verification;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountVerificationResult {
  private boolean verified;
  private String resolvedAccountName;
  private String reason;
}
