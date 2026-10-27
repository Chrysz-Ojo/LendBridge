package com.lendbridge.payment.wallet;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Mirrors wallet-service's own {@code RegisterVirtualAccountRequest}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterVirtualAccountCommand {
  private String bankName;
  private String accountNumber;
  private String providerReference;
}
