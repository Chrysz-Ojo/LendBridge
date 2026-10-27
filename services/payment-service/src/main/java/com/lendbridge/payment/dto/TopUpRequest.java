package com.lendbridge.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code customerEmail} is supplied by the caller rather than fetched from user-profile-service —
 * payment-service's only synchronous dependencies are wallet-service and authentication-service
 * (see the approved plan); adding a user-profile-service call for one field isn't worth a new
 * cross-service dependency, and the client already has it from the logged-in session.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TopUpRequest {

  @Positive private long amountKobo;

  @NotBlank private String currency;

  @NotBlank
  @Email
  private String customerEmail;
}
