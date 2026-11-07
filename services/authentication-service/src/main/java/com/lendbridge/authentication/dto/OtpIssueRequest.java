package com.lendbridge.authentication.dto;

import com.lendbridge.authentication.model.OtpPurpose;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code destinationEmail} is optional: null delivers over SMS only (phone verification, SMS
 * MFA); set it to deliver the same code over both SMS and email (e.g. a forgotten transaction
 * PIN, E12-S3, so the flow doesn't depend on a single channel being reachable). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpIssueRequest {
  private OtpPurpose purpose;
  private String destinationPhone;
  private String destinationEmail;
}
