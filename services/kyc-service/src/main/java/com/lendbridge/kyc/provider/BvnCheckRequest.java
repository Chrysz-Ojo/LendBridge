package com.lendbridge.kyc.provider;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code expectedFullName}/{@code expectedDateOfBirth} are self-attested by the borrower on
 * {@code StartVerificationRequest} — kyc-service has no synchronous dependency on
 * user-profile-service (the approved plan's package layout names none), so these are supplied
 * directly by the caller rather than fetched cross-service, the same judgment call payment-service
 * made for {@code TopUpRequest.customerEmail}. An Enhanced-KYC-style provider (Smile ID, Dojah,
 * Prembly all offer this) compares them against the BVN registry's own biodata and returns the
 * match verdict.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BvnCheckRequest {
  private String bvn;
  private String expectedFullName;
  private LocalDate expectedDateOfBirth;
}
