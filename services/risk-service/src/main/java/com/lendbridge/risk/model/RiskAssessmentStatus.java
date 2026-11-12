package com.lendbridge.risk.model;

/** {@code PENDING_KYC} exists so a loan submitted before its borrower's KYC clears is never
 * silently dropped — see the approved plan's symmetric retroactive-grading design. */
public enum RiskAssessmentStatus {
  PENDING_KYC,
  GRADED,
  KYC_REJECTED
}
