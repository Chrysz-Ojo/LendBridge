package com.lendbridge.authentication.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

  private boolean mfaRequired;
  private String mfaChallengeToken;
  private TokenResponse tokens;

  public static LoginResponse mfaRequired(String challengeToken) {
    return new LoginResponse(true, challengeToken, null);
  }

  public static LoginResponse issued(TokenResponse tokens) {
    return new LoginResponse(false, null, tokens);
  }
}
