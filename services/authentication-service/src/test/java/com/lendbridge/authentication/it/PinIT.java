package com.lendbridge.authentication.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.authentication.dto.LoginResponse;
import com.lendbridge.authentication.notification.FakeEmailSender;
import com.lendbridge.authentication.notification.FakeSmsSender;
import com.lendbridge.authentication.support.AbstractIntegrationTest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class PinIT extends AbstractIntegrationTest {

  @Autowired private ObjectMapper objectMapper;
  @Autowired private FakeEmailSender fakeEmailSender;
  @Autowired private FakeSmsSender fakeSmsSender;

  private static final Pattern CODE_PATTERN = Pattern.compile("(\\d{6})");

  @Test
  void setPin_thenVerify_succeeds() throws Exception {
    String accessToken = registerAndLogin();

    mockMvc
        .perform(
            post("/api/v1/auth/pin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("pin", "4821"))))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/auth/pin/verify")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("pin", "4821"))))
        .andExpect(status().isOk());
  }

  @Test
  void setPin_rejectsAnObviousPattern() throws Exception {
    String accessToken = registerAndLogin();

    mockMvc
        .perform(
            post("/api/v1/auth/pin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("pin", "1234"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void setPin_rejectsRepeatedPairsAndRepeatingSequences() throws Exception {
    String accessToken = registerAndLogin();

    for (String weakPin : new String[] {"0000", "1212", "565656", "123123"}) {
      mockMvc
          .perform(
              post("/api/v1/auth/pin")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("pin", weakPin))))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  void setPin_acceptsANonRepeatingNonSequentialPin() throws Exception {
    String accessToken = registerAndLogin();

    mockMvc
        .perform(
            post("/api/v1/auth/pin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("pin", "281967"))))
        .andExpect(status().isCreated());
  }

  @Test
  void verifyPin_fiveWrongAttempts_locksPin_andCorrectPinIsStillRejected() throws Exception {
    String accessToken = registerAndLogin();
    setPin(accessToken, "4821");

    for (int i = 0; i < 5; i++) {
      mockMvc
          .perform(
              post("/api/v1/auth/pin/verify")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("pin", "0000"))))
          .andExpect(status().isUnauthorized());
    }

    mockMvc
        .perform(
            post("/api/v1/auth/pin/verify")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("pin", "4821"))))
        .andExpect(status().isLocked());
  }

  @Test
  void changePin_withCorrectCurrentPin_succeeds_wrongCurrentPinFails() throws Exception {
    String accessToken = registerAndLogin();
    setPin(accessToken, "4821");

    mockMvc
        .perform(
            put("/api/v1/auth/pin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("currentPin", "9999", "newPin", "5566"))))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            put("/api/v1/auth/pin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("currentPin", "4821", "newPin", "5566"))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/auth/pin/verify")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("pin", "5566"))))
        .andExpect(status().isOk());
  }

  @Test
  void forgottenPin_resetViaPasswordAndDualChannelCode_succeeds() throws Exception {
    String email = "pinreset." + UUID.randomUUID() + "@example.com";
    String phone = "+234" + (800_000_0000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L));
    createActiveUser(email, phone, "Str0ng!Passw0rd");
    String accessToken = loginAndGetAccessToken(email, "Str0ng!Passw0rd");
    setPin(accessToken, "4821");

    mockMvc
        .perform(
            post("/api/v1/auth/pin/reset/challenge")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("password", "Str0ng!Passw0rd"))))
        .andExpect(status().isOk());

    String emailCode = extractCode(fakeEmailSender.sentEmails().get(fakeEmailSender.sentEmails().size() - 1).getBody());
    String smsCode = extractCode(fakeSmsSender.sentMessages().get(fakeSmsSender.sentMessages().size() - 1).getMessage());
    assertThat(emailCode).isEqualTo(smsCode);

    mockMvc
        .perform(
            post("/api/v1/auth/pin/reset/verify")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", emailCode, "newPin", "7788"))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/auth/pin/verify")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("pin", "7788"))))
        .andExpect(status().isOk());
  }

  @Test
  void requestPinReset_wrongPassword_isRejected() throws Exception {
    String accessToken = registerAndLogin();

    mockMvc
        .perform(
            post("/api/v1/auth/pin/reset/challenge")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("password", "WrongPassword123!"))))
        .andExpect(status().isUnauthorized());
  }

  private void setPin(String accessToken, String pin) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/pin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("pin", pin))))
        .andExpect(status().isCreated());
  }

  private String extractCode(String text) {
    Matcher matcher = CODE_PATTERN.matcher(text);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private String registerAndLogin() throws Exception {
    String email = "pin." + UUID.randomUUID() + "@example.com";
    String phone = "+234" + (800_000_0000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L));
    createActiveUser(email, phone, "Str0ng!Passw0rd");
    return loginAndGetAccessToken(email, "Str0ng!Passw0rd");
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
