package com.lendbridge.loan.model;

/**
 * Phase 1 only reaches as far as {@code LISTED}/{@code REJECTED}/{@code CANCELLED} — the PRD's
 * full lifecycle also has {@code FUNDING}, {@code FUNDED}, {@code ACTIVE}, {@code REPAID},
 * {@code DEFAULTED}, {@code WRITTEN_OFF} and {@code EXPIRED}, all of which need the funding saga
 * (investment-service, marketplace-service, wallet hold-capture, ledger escrow) that doesn't exist
 * yet. See docs/adr/0015-loan-service-phase-1-scope.md.
 */
public enum LoanStatus {
  DRAFT,
  UNDER_REVIEW,
  LISTED,
  REJECTED,
  CANCELLED
}
