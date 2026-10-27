package com.lendbridge.payment.provider;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code bvnOrNin} is nullable — Flutterwave requires it for a static virtual account, but
 * there's no KYC-service yet to source a trusted value from, so this is null until KYC exists
 * (same deferred-gap class as wallet-service's bank-name-verification check). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VirtualAccountRequest {
  private String walletId;
  private String customerEmail;
  private String customerName;
  private String bvnOrNin;
}
