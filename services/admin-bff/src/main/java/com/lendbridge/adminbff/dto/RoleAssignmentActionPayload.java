package com.lendbridge.adminbff.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The shape {@code ApprovalRequest.actionPayload} must hold for {@code ROLE_ASSIGNMENT} —
 * {@code {"targetUserId":"...","role":"SUPER_ADMIN"}} — parsed by {@code ApprovalServiceImpl} on
 * approve, never exposed on any controller endpoint directly. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoleAssignmentActionPayload {
  private UUID targetUserId;
  private Role role;
}
