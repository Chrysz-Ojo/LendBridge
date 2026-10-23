package com.lendbridge.authentication.dto;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
  private UUID id;
  private String email;
  private String fullName;
  private String status;
  private boolean emailVerified;
  private boolean phoneVerified;
  private boolean mfaEnabled;
  private List<String> roles;
}
