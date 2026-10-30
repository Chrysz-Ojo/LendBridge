package com.lendbridge.loan.model;

/** Unlike kyc-service's {@code ReviewDecision}, there is no {@code REQUEST_INFO} option — the PRD's
 * loan lifecycle table only has {@code Under review -> Listed, Rejected}. */
public enum LoanReviewDecision {
  APPROVE,
  REJECT
}
