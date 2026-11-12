package com.lendbridge.adminbff.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code decision} (e.g. {@code APPROVE}/{@code REJECT}, loan-service's own
 * {@code LoanReviewDecision} enum values) and an optional grade-override reason, forwarded as-is —
 * loan-service enforces the valid set and the "reason required for reject" rule itself. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoanReviewDecisionRequest {
  @NotBlank private String decision;
  private String reason;
}
