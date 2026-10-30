package com.lendbridge.loan.kyc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A loan-service-local shape for kyc-service's {@code GET /api/v1/kyc/verifications/me} response
 * — only the fields loan-service actually needs. Deliberately not a shared/reused DTO class
 * (CLAUDE.md: each service owns its database and, by the same bounded-context reasoning, its own
 * view of another service's API contract); {@code @JsonIgnoreProperties} tolerates the extra
 * fields kyc-service's real response carries (badges, reviewRequired, etc.). */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KycStatusResponse {
  private String status;
  private String trustTier;
}
