package com.lendbridge.profile.model;

/** A user's business role on the platform — distinct from authentication-service's staff
 * {@code Role} enum (SUPER_ADMIN, KYC_OFFICER, ...), which governs back-office permissions. */
public enum ProfileRole {
  LENDER,
  BORROWER
}
