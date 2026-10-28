package com.lendbridge.kyc.document;

/** Mirrors document-service's own {@code ScanStatus} enum by name — see {@link DocumentType}. */
public enum ScanStatus {
  PENDING,
  CLEAN,
  INFECTED
}
