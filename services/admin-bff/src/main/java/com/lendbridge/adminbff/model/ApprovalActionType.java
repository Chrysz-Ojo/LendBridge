package com.lendbridge.adminbff.model;

/**
 * Four sensitive staff actions wired through maker-checker in Phase 1 (see ADR 0023) — each one
 * the sole trigger for its underlying action; there is no direct passthrough endpoint for any of
 * them (see each action's gateway javadoc). {@link #requiredRole()} is the role a caller must
 * hold to either raise or decide an approval of that type — enforced in
 * {@code ApprovalServiceImpl}, not at the controller layer, since {@code ApprovalController}
 * itself accepts any staff role generically.
 *
 * <p>Extensible: adding a new maker-checker action means adding an enum value with its required
 * role here, an action-payload DTO, and a matching dispatch branch in
 * {@code ApprovalServiceImpl.execute(...)}.
 */
public enum ApprovalActionType {
  ROLE_ASSIGNMENT("SUPER_ADMIN"),
  LOAN_REVIEW_DECISION("RISK_OFFICER"),
  CHANNEL_POLICY_UPDATE("SUPER_ADMIN"),
  RECONCILIATION_RUN("FINANCE");

  private final String requiredRole;

  ApprovalActionType(String requiredRole) {
    this.requiredRole = requiredRole;
  }

  public String requiredRole() {
    return requiredRole;
  }
}
