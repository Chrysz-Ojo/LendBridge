package com.lendbridge.kyc.model;

/**
 * {@code PASSED}/{@code MISMATCH} are routing-relevant verdicts a provider returns synchronously.
 * {@code FAILED} is a definitive provider negative (e.g. BVN/NIN does not exist, fraud flag) and is
 * never used to record a transient provider/dependency outage — see the approved plan's outcome
 * routing rule 4.
 */
public enum CheckOutcome {
  PASSED,
  MISMATCH,
  FAILED
}
