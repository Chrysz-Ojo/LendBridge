package com.lendbridge.loan.model;

/** PRD E6-S1: "purpose from a list plus free text" — the free text lives in
 * {@code LoanApplication.purposeDetail}. */
public enum LoanPurpose {
  BUSINESS,
  EDUCATION,
  MEDICAL,
  HOME_IMPROVEMENT,
  DEBT_CONSOLIDATION,
  OTHER
}
