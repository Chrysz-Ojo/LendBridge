package com.lendbridge.authentication.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.authentication.support.AbstractIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class RegistrationValidationIT extends AbstractIntegrationTest {

  @Autowired private ObjectMapper objectMapper;

  @Test
  void rejectsWeakPassword() throws Exception {
    Map<String, Object> request =
        Map.of(
            "fullName", "Weak Pass",
            "email", "weak." + UUID.randomUUID() + "@example.com",
            "phone", randomPhone(),
            "password", "weak",
            "acceptTerms", true,
            "acceptPrivacy", true);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsDuplicateEmailAndPhone() throws Exception {
    String email = "dup." + UUID.randomUUID() + "@example.com";
    String phone = randomPhone();
    Map<String, Object> request =
        Map.of(
            "fullName", "Dup User",
            "email", email,
            "phone", phone,
            "password", "Str0ng!Passw0rd",
            "acceptTerms", true,
            "acceptPrivacy", true);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    Map<String, Object> sameEmailDifferentPhone =
        Map.of(
            "fullName", "Dup User 2",
            "email", email,
            "phone", randomPhone(),
            "password", "Str0ng!Passw0rd",
            "acceptTerms", true,
            "acceptPrivacy", true);
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sameEmailDifferentPhone)))
        .andExpect(status().isConflict());

    Map<String, Object> samePhoneDifferentEmail =
        Map.of(
            "fullName", "Dup User 3",
            "email", "other." + UUID.randomUUID() + "@example.com",
            "phone", phone,
            "password", "Str0ng!Passw0rd",
            "acceptTerms", true,
            "acceptPrivacy", true);
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(samePhoneDifferentEmail)))
        .andExpect(status().isConflict());
  }

  @Test
  void rejectsMissingConsent() throws Exception {
    Map<String, Object> request =
        Map.of(
            "fullName", "No Consent",
            "email", "noconsent." + UUID.randomUUID() + "@example.com",
            "phone", randomPhone(),
            "password", "Str0ng!Passw0rd",
            "acceptTerms", false,
            "acceptPrivacy", true);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  private String randomPhone() {
    return "+234" + (800_000_0000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L));
  }
}
