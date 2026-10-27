package com.lendbridge.authentication.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.authentication.dto.LoginResponse;
import com.lendbridge.authentication.dto.TokenResponse;
import com.lendbridge.authentication.dto.TotpEnrollResponse;
import com.lendbridge.authentication.crypto.TotpAlgorithm;
import com.lendbridge.authentication.crypto.Base32;
import com.lendbridge.starter.dto.ApiResponse;
import com.lendbridge.authentication.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;

class MfaIT extends AbstractIntegrationTest {

  @Autowired private ObjectMapper objectMapper;

  @Test
  void enrollingTotpThenRequiresItOnNextLogin() throws Exception {
    String email = "mfa." + UUID.randomUUID() + "@example.com";
    String phone = "+234" + (800_000_0000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L));
    createActiveUser(email, phone, "Str0ng!Passw0rd");

    String accessToken = loginAndGetAccessToken(email, "Str0ng!Passw0rd");

    String enrollBody =
        mockMvc
            .perform(
                post("/api/v1/auth/mfa/totp/enroll")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    TotpEnrollResponse enrollResponse =
        objectMapper
            .readValue(enrollBody, new TypeReference<ApiResponse<TotpEnrollResponse>>() {})
            .getData();

    String code = currentTotpCode(enrollResponse.getSecret());
    mockMvc
        .perform(
            post("/api/v1/auth/mfa/totp/verify")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", code))))
        .andExpect(status().isOk());

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
    assertThat(login.isMfaRequired()).isTrue();
    assertThat(login.getMfaChallengeToken()).isNotBlank();

    String secondCode = currentTotpCode(enrollResponse.getSecret());
    String tokenBody =
        mockMvc
            .perform(
                post("/api/v1/auth/login/mfa")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "challengeToken", login.getMfaChallengeToken(),
                                "method", "TOTP",
                                "code", secondCode))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    TokenResponse tokens =
        objectMapper
            .readValue(tokenBody, new TypeReference<ApiResponse<TokenResponse>>() {})
            .getData();
    assertThat(tokens.getAccessToken()).isNotBlank();
  }

  @Test
  void stepUpVerify_succeedsWithValidActiveCode_andRejectsWrongCode() throws Exception {
    String email = "stepup." + UUID.randomUUID() + "@example.com";
    String phone = "+234" + (800_000_0000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L));
    createActiveUser(email, phone, "Str0ng!Passw0rd");
    String accessToken = loginAndGetAccessToken(email, "Str0ng!Passw0rd");

    String enrollBody =
        mockMvc
            .perform(
                post("/api/v1/auth/mfa/totp/enroll")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    TotpEnrollResponse enrollResponse =
        objectMapper
            .readValue(enrollBody, new TypeReference<ApiResponse<TotpEnrollResponse>>() {})
            .getData();
    mockMvc
        .perform(
            post("/api/v1/auth/mfa/totp/verify")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", currentTotpCode(enrollResponse.getSecret())))))
        .andExpect(status().isOk());

    // Now ACTIVE (not PENDING) — step-up-verify must still accept a fresh code.
    mockMvc
        .perform(
            post("/api/v1/auth/mfa/totp/step-up-verify")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", currentTotpCode(enrollResponse.getSecret())))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/auth/mfa/totp/step-up-verify")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", "000000"))))
        .andExpect(status().isUnauthorized());
  }

  private String currentTotpCode(String base32Secret) {
    byte[] secretBytes = Base32.decode(base32Secret);
    return TotpAlgorithm.generateAtTime(
        secretBytes, Instant.now().getEpochSecond(), 30, 6);
  }

  private String loginAndGetAccessToken(String email, String password) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("identifier", email, "password", password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper
        .readValue(body, new TypeReference<ApiResponse<LoginResponse>>() {})
        .getData()
        .getTokens()
        .getAccessToken();
  }
}
