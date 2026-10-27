package com.lendbridge.payment.provider;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PayoutRequest {
  private String reference;
  private long amountKobo;
  private String currency;
  private String bankCode;
  private String accountNumber;
  private String accountHolderName;
  private String idempotencyKey;
}
