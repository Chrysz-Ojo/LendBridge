package com.lendbridge.authentication.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.authentication.dto.LoginResponse;
import com.lendbridge.authentication.dto.TokenResponse;
import com.lendbridge.starter.dto.ApiResponse;
import com.lendbridge.authentication.support.AbstractIntegrationTest;
import com.lendbridge.authentication.model.User;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class LoginIT extends AbstractIntegrationTest {

  @Autowired private ObjectMapper objectMapper;

  @Test
  void canLoginByEmailAndByPhone() throws Exception {
    String email = "login." + UUID.randomUUID() + "@example.com";
    String phone = randomPhone();
    User user = createActiveUser(email, phone, "Str0ng!Passw0rd");

    String byEmail =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("identifier", email, "password", "Str0ng!Passw0rd"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    LoginResponse loginResponse =
        objectMapper
            .readValue(byEmail, new TypeReference<ApiResponse<LoginResponse>>() {})
            .getData();
    assertThat(loginResponse.isMfaRequired()).isFalse();
    assertThat(loginResponse.getTokens().getAccessToken()).isNotBlank();
    assertThat(loginResponse.getTokens().getRefreshToken()).isNotBlank();

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("identifier", phone, "password", "Str0ng!Passw0rd"))))
        .andExpect(status().isOk());

    assertThat(userRepository.findById(user.getId()).orElseThrow().getFailedLoginAttempts())
        .isZero();
  }

  @Test
  void locksAccountAfterFiveFailedAttempts() throws Exception {
    String email = "lockout." + UUID.randomUUID() + "@example.com";
    createActiveUser(email, randomPhone(), "Str0ng!Passw0rd");

    for (int i = 0; i < 4; i++) {
      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          Map.of("identifier", email, "password", "WrongPassword!1"))))
          .andExpect(status().isUnauthorized());
    }

    // 5th failure locks the account.
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("identifier", email, "password", "WrongPassword!1"))))
        .andExpect(status().isUnauthorized());

    // Even the correct password is now rejected with 423 Locked.
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("identifier", email, "password", "Str0ng!Passw0rd"))))
        .andExpect(status().isLocked());
  }

  @Test
  void refreshRotatesTokenAndDetectsReuse() throws Exception {
    String email = "refresh." + UUID.randomUUID() + "@example.com";
    createActiveUser(email, randomPhone(), "Str0ng!Passw0rd");

    String loginBody =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("identifier", email, "password", "Str0ng!Passw0rd"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    LoginResponse login =
        objectMapper
            .readValue(loginBody, new TypeReference<ApiResponse<LoginResponse>>() {})
            .getData();
    String originalRefreshToken = login.getTokens().getRefreshToken();

    String refreshedBody =
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(Map.of("refreshToken", originalRefreshToken))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    TokenResponse refreshed =
        objectMapper
            .readValue(refreshedBody, new TypeReference<ApiResponse<TokenResponse>>() {})
            .getData();
    assertThat(refreshed.getRefreshToken()).isNotEqualTo(originalRefreshToken);

    // Replaying the original (now-revoked) refresh token must be treated as theft.
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("refreshToken", originalRefreshToken))))
        .andExpect(status().isUnauthorized());

    // The whole family (including the newest token) must now be revoked.
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("refreshToken", refreshed.getRefreshToken()))))
        .andExpect(status().isUnauthorized());
  }

  private String randomPhone() {
    return "+234" + (800_000_0000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L));
  }
}
