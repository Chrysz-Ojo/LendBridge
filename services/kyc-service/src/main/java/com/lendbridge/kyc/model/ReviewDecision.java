package com.lendbridge.kyc.model;

/**
 * An officer's explicit decision on a queued case. {@code REQUEST_INFO} is the only way a case
 * reaches {@link VerificationStatus#NEEDS_MORE_INFO} — that state is never set automatically (see
 * the approved plan's outcome routing rule 2).
 */
public enum ReviewDecision {
  APPROVE,
  REJECT,
  REQUEST_INFO
}
