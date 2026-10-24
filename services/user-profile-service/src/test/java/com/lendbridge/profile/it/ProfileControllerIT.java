package com.lendbridge.profile.it;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.profile.model.UserProfile;
import com.lendbridge.profile.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ProfileControllerIT extends AbstractIntegrationTest {

  @Test
  void getProfile_returns404_whenNoProfileShellExistsYet() throws Exception {
    UUID userId = UUID.randomUUID();

    mockMvc
        .perform(get("/api/v1/profile/me").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isNotFound());
  }

  @Test
  void getProfile_returnsExistingProfile() throws Exception {
    UUID userId = createProfile();

    mockMvc
        .perform(get("/api/v1/profile/me").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId", equalTo(userId.toString())));
  }

  @Test
  void patchProfile_partiallyUpdatesFields() throws Exception {
    UUID userId = createProfile();

    String body = "{\"fullName\":\"Updated Name\"}";

    mockMvc
        .perform(
            patch("/api/v1/profile/me")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fullName", equalTo("Updated Name")));
  }

  @Test
  void addRole_thenListRoles_reflectsNewRole() throws Exception {
    UUID userId = createProfile();

    mockMvc
        .perform(
            post("/api/v1/profile/me/roles")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"LENDER\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.roles[0]", equalTo("LENDER")));

    mockMvc
        .perform(get("/api/v1/profile/me/roles").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.roles[0]", equalTo("LENDER")));
  }

  private UUID createProfile() {
    UUID userId = UUID.randomUUID();
    Instant now = Instant.now();
    userProfileRepository.save(
        UserProfile.builder()
            .id(com.lendbridge.starter.crypto.UuidV7.generate())
            .userId(userId)
            .email(userId + "@example.com")
            .createdAt(now)
            .updatedAt(now)
            .build());
    return userId;
  }
}
