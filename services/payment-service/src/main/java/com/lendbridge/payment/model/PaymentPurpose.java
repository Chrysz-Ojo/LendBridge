package com.lendbridge.payment.model;

/** {@code REPAYMENT} is schema-complete only in v1 — Loan-service, which would actually
 * orchestrate repayments, doesn't exist yet. */
public enum PaymentPurpose {
  TOP_UP,
  REPAYMENT
}
