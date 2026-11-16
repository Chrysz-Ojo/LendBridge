package com.lendbridge.loan.model;

/** Mirrors payment-service's own {@code RepaymentMethod} exactly (same 4 values, same reasoning)
 * — that enum's own javadoc already named this: "a wallet-funded repayment is a pure
 * ledger-to-ledger transfer the not-yet-built Loan-service would orchestrate directly." Only
 * {@code WALLET} is live; the other three are schema-reserved and rejected. */
public enum RepaymentMethod {
  WALLET,
  CARD,
  BANK_TRANSFER,
  DIRECT_DEBIT
}
