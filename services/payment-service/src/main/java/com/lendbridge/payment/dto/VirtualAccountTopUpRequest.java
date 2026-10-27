package com.lendbridge.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code bvnOrNin} is nullable — Flutterwave requires it to provision a dedicated virtual
 * account, Paystack and Monnify don't; no KYC-service exists yet to source it from, so it must
 * come from the caller when the active provider needs it (same deferred-gap class as
 * wallet-service's bank-name verification).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VirtualAccountTopUpRequest {

  @NotBlank
  @Email
  private String customerEmail;

  @NotBlank private String customerName;

  private String bvnOrNin;
}
