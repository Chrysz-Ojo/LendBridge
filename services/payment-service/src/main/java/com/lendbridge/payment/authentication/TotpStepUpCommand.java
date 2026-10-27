package com.lendbridge.payment.authentication;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Mirrors authentication-service's own {@code TotpVerifyRequest}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TotpStepUpCommand {
  private String code;
}
