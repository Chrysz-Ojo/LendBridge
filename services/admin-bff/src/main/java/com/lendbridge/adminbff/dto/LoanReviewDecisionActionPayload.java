package com.lendbridge.adminbff.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The shape {@code ApprovalRequest.actionPayload} must hold for {@code LOAN_REVIEW_DECISION} —
 * {@code {"loanId":"...","decision":"APPROVE","reason":"..."}} — parsed by
 * {@code ApprovalServiceImpl} on approve, never exposed on any controller endpoint directly.
 * Bundles the loan id that the old direct endpoint took as a path variable, since a generic
 * actionPayload has no separate path-variable concept. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoanReviewDecisionActionPayload {
  private UUID loanId;
  private String decision;
  private String reason;
}
