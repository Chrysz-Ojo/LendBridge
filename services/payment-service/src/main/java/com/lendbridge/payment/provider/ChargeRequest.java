package com.lendbridge.payment.provider;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChargeRequest {
  private String reference;
  private long amountKobo;
  private String currency;
  private String customerEmail;
  private String idempotencyKey;
}
