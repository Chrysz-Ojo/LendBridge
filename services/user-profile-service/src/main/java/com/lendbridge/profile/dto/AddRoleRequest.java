package com.lendbridge.profile.dto;

import com.lendbridge.profile.model.ProfileRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddRoleRequest {
  @NotNull private ProfileRole role;
}
