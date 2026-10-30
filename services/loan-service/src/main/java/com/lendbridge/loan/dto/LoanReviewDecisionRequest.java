package com.lendbridge.loan.dto;

import com.lendbridge.loan.model.LoanReviewDecision;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code reason} is required for {@code REJECT} (enforced in {@code LoanReviewServiceImpl}, not
 * bean validation, since it's conditional on {@code decision}) — same pattern as kyc-service's
 * {@code ReviewDecisionRequest}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoanReviewDecisionRequest {
  @NotNull private LoanReviewDecision decision;
  private String reason;
}
