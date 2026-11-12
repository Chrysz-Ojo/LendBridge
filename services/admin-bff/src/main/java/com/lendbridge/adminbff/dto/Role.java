package com.lendbridge.adminbff.dto;

/** Local copy of authentication-service's own {@code Role} enum — admin-bff never depends on that
 * service's module, same "local copy, no module dependency" idiom as risk-service's
 * {@code TrustTier}. Needed as a real enum (not a passthrough {@code String}, unlike most other
 * local DTO fields in this service) because {@code ApprovalServiceImpl} constructs the actual
 * {@link RoleAssignmentRequest} body itself from the approval's stored payload. */
public enum Role {
  SUPER_ADMIN,
  KYC_OFFICER,
  RISK_OFFICER,
  FINANCE,
  SUPPORT
}
