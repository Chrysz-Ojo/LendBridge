package com.lendbridge.payment.model;

/** No v1 code path creates or transitions these — see ADR 0011. Modeled now so the schema never
 * needs to change once direct debit is actually built. */
public enum MandateStatus {
  PENDING_AUTHORIZATION,
  ACTIVE,
  REVOKED,
  EXPIRED
}
