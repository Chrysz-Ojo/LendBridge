package com.lendbridge.loan.model;

/** {@code REQUESTED} is the in-flight window between a borrower calling
 * {@code POST /loans/{id}/repayments} and the ledger posting resolving — it's the status-guard
 * that makes that endpoint idempotent (a second call while already {@code REQUESTED} fails fast
 * with a conflict, same pattern as {@code LoanStatus.DISBURSING} guarding
 * {@code POST /agreement/sign}), and it reverts back to {@code PENDING} if the repayment fails. */
public enum InstalmentStatus {
  PENDING,
  REQUESTED,
  PAID
}
