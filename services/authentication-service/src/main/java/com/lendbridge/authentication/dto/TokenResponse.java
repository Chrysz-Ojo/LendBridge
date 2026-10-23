package com.lendbridge.authentication.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {
  private String accessToken;
  private String refreshToken;
  private String tokenType;
  private long expiresInSeconds;
}
