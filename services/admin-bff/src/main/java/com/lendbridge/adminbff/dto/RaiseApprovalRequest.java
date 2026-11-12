package com.lendbridge.adminbff.dto;

import com.lendbridge.adminbff.model.ApprovalActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code actionPayload} is raw JSON text matching whatever {@code actionType} expects — for
 * {@code ROLE_ASSIGNMENT}: {@code {"targetUserId":"...","role":"SUPER_ADMIN"}}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RaiseApprovalRequest {
  @NotNull private ApprovalActionType actionType;
  @NotBlank private String actionPayload;
}
