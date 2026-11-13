package com.lendbridge.loan.model;

/**
 * The funding saga (see docs/adr/0018-funding-saga-event-choreography.md) adds the six states
 * after {@code LISTED}: {@code FUNDING} (first commitment), {@code FUNDED} (100% committed),
 * {@code DISBURSING} (agreement signed, awaiting the ledger postings), {@code ACTIVE} (disbursed,
 * repayment schedule persisted), {@code REPAID} and {@code DEFAULTED}. {@code WRITTEN_OFF} and
 * {@code EXPIRED} stay out of scope (default-recovery and listing-expiry cron, neither of which
 * exists yet). {@code REPAID}/{@code DEFAULTED} have no transition logic yet either — wallet-funded
 * repayment processing is separate, not-yet-built scope.
 */
public enum LoanStatus {
  DRAFT,
  UNDER_REVIEW,
  LISTED,
  REJECTED,
  CANCELLED,
  FUNDING,
  FUNDED,
  DISBURSING,
  ACTIVE,
  REPAID,
  DEFAULTED
}
