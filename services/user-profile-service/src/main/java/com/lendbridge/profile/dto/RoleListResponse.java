package com.lendbridge.profile.dto;

import com.lendbridge.profile.model.ProfileRole;
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
  private List<ProfileRole> roles;
}
