package com.lendbridge.authentication.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.authentication.dto.LoginResponse;
import com.lendbridge.authentication.model.Role;
import com.lendbridge.authentication.model.UserRoleAssignment;
import com.lendbridge.authentication.repository.UserRoleAssignmentRepository;
import com.lendbridge.authentication.crypto.UuidV7;
import com.lendbridge.authentication.dto.ApiResponse;
import com.lendbridge.authentication.support.AbstractIntegrationTest;
import com.lendbridge.authentication.model.User;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class RoleAssignmentIT extends AbstractIntegrationTest {

  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRoleAssignmentRepository roleAssignmentRepository;

  @Test
  void onlySuperAdminCanAssignRoles() throws Exception {
    String adminEmail = "admin." + UUID.randomUUID() + "@example.com";
    User admin = createActiveUser(adminEmail, randomPhone(), "Str0ng!Passw0rd");
    roleAssignmentRepository.save(
        UserRoleAssignment.builder()
            .id(UuidV7.generate())
            .userId(admin.getId())
            .role(Role.SUPER_ADMIN)
            .createdAt(Instant.now())
            .build());

    String plainEmail = "plain." + UUID.randomUUID() + "@example.com";
    User plainUser = createActiveUser(plainEmail, randomPhone(), "Str0ng!Passw0rd");

    String adminToken = loginAndGetAccessToken(adminEmail, "Str0ng!Passw0rd");
    String plainToken = loginAndGetAccessToken(plainEmail, "Str0ng!Passw0rd");

    mockMvc
        .perform(
            post("/api/v1/auth/admin/users/" + plainUser.getId() + "/roles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("role", "SUPPORT"))))
        .andExpect(status().isForbidden());

    String response =
        mockMvc
            .perform(
                post("/api/v1/auth/admin/users/" + plainUser.getId() + "/roles")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("role", "SUPPORT"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(response).contains("SUPPORT");

    mockMvc
        .perform(
            get("/api/v1/auth/admin/users/" + plainUser.getId() + "/roles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk());
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

  private String randomPhone() {
    return "+234" + (800_000_0000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L));
  }
}
