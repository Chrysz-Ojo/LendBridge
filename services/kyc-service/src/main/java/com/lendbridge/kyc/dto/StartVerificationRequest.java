package com.lendbridge.kyc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code fullName}/{@code dateOfBirth} are self-attested by the borrower rather than fetched from
 * user-profile-service — kyc-service's only synchronous dependency is document-service (see the
 * approved plan), so adding a user-profile-service call for two fields isn't worth a new
 * cross-service dependency (same judgment call payment-service made for
 * {@code TopUpRequest.customerEmail}). {@code nin}/{@code bankStatementDocumentId} are optional:
 * Tier 1 only needs BVN + liveness/face-match.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StartVerificationRequest {

  @NotBlank
  @Pattern(regexp = "\\d{11}", message = "bvn must be 11 digits")
  private String bvn;

  @Pattern(regexp = "\\d{11}", message = "nin must be 11 digits")
  private String nin;

  @NotBlank private String fullName;

  @NotNull private LocalDate dateOfBirth;

  @NotNull private UUID idCardDocumentId;

  @NotNull private UUID selfieDocumentId;

  private UUID bankStatementDocumentId;
}
