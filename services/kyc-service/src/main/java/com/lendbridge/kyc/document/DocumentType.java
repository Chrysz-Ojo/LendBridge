package com.lendbridge.kyc.document;

/**
 * Mirrors document-service's own {@code DocumentType} enum by name — kyc-service never shares a
 * module with document-service ("each service owns its database"), so this is a local copy kept
 * in sync at the JSON-contract level, the same way any REST client models the shape it depends on.
 */
public enum DocumentType {
  ID_CARD,
  SELFIE,
  BANK_STATEMENT,
  INCOME_STATEMENT,
  BUSINESS_PLAN,
  LOAN_AGREEMENT,
  OTHER
}
