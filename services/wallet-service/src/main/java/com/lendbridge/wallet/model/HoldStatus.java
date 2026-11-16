package com.lendbridge.wallet.model;

/**
 * {@code CONVERTED} means the funding saga captured this hold — the lender's reservation became a
 * real ledger debit (see {@code InvestmentHoldsCaptureRequestedEventConsumer}). It's a pure status
 * flip, never itself a ledger posting — {@code com.lendbridge.ledger.loansaga.LoanDisbursementPoster}
 * is what actually moves money; this just marks the hold's lifecycle as settled so it can't be
 * captured or released twice. No Liquibase change was needed to add it, exactly as anticipated:
 * {@code status} is a plain {@code varchar}, not a native Postgres enum type.
 */
public enum HoldStatus {
  ACTIVE,
  RELEASED,
  CONVERTED
}
