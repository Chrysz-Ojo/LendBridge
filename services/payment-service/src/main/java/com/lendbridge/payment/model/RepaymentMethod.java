package com.lendbridge.payment.model;

/** {@code WALLET} never touches a PSP at all — a wallet-funded repayment is a pure
 * ledger-to-ledger transfer the not-yet-built Loan-service would orchestrate directly.
 * {@code DIRECT_DEBIT} is schema-reserved only in v1 (see ADR 0011) — selecting it throws
 * {@code UnprocessableEntityException}. */
public enum RepaymentMethod {
  WALLET,
  CARD,
  BANK_TRANSFER,
  DIRECT_DEBIT
}
