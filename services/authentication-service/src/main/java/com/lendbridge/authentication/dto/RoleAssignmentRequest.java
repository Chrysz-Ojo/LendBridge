package com.lendbridge.authentication.dto;

import com.lendbridge.authentication.model.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoleAssignmentRequest {

  @NotNull private Role role;
}
