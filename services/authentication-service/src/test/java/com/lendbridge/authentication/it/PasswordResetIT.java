package com.lendbridge.authentication.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.authentication.dto.LoginResponse;
import com.lendbridge.authentication.notification.FakeEmailSender;
import com.lendbridge.starter.dto.ApiResponse;
import com.lendbridge.authentication.support.AbstractIntegrationTest;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class PasswordResetIT extends AbstractIntegrationTest {

  @Autowired private ObjectMapper objectMapper;
  @Autowired private FakeEmailSender fakeEmailSender;

  private static final Pattern TOKEN_PATTERN = Pattern.compile("token=(\\S+)");

  @Test
  void resettingPasswordRevokesExistingSessions() throws Exception {
    String email = "reset." + UUID.randomUUID() + "@example.com";
    String phone = "+234" + (800_000_0000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L));
    createActiveUser(email, phone, "Str0ng!Passw0rd");

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
    String refreshToken =
        objectMapper
            .readValue(loginBody, new TypeReference<ApiResponse<LoginResponse>>() {})
            .getData()
            .getTokens()
            .getRefreshToken();

    mockMvc
        .perform(
            post("/api/v1/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("identifier", email))))
        .andExpect(status().isOk());

    Matcher matcher =
        TOKEN_PATTERN.matcher(
            fakeEmailSender.sentEmails().get(fakeEmailSender.sentEmails().size() - 1).getBody());
    assertThat(matcher.find()).isTrue();
    String resetToken = matcher.group(1);

    mockMvc
        .perform(
            post("/api/v1/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("token", resetToken, "newPassword", "N3wStr0ng!Passw0rd"))))
        .andExpect(status().isOk());

    // Old refresh token must now be revoked.
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
        .andExpect(status().isUnauthorized());

    // Login with the new password succeeds.
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("identifier", email, "password", "N3wStr0ng!Passw0rd"))))
        .andExpect(status().isOk());
  }
}
