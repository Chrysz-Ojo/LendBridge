package com.lendbridge.adminbff.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code decision} is a plain String ({@code APPROVE}/{@code REJECT}/{@code REQUEST_INFO} per
 * kyc-service's own {@code ReviewDecision} enum) — kyc-service itself enforces the valid set and
 * the "reason required for reject" rule; admin-bff just forwards the caller's input. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KycReviewDecisionRequest {
  @NotBlank private String decision;
  private String reason;
}
