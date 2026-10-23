package com.lendbridge.authentication.dto;

import com.lendbridge.authentication.model.Role;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoleListResponse {
  private List<Role> roles;
}
