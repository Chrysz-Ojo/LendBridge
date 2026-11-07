package com.lendbridge.notification.it;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.notification.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Role-gating (`@PreAuthorize`) is a Spring-context-level concern, not provable by a unit test in
 * isolation — this is the one place it actually needs an integration test, same reasoning as
 * {@code kyc-service}'s {@code ReviewControllerIT} for its own role-gated endpoint. */
class ChannelPolicyControllerIT extends AbstractIntegrationTest {

  private static final GrantedAuthority SUPER_ADMIN_AUTHORITY = new SimpleGrantedAuthority("ROLE_SUPER_ADMIN");

  @Test
  void listAll_withoutSuperAdminRole_isForbidden() throws Exception {
    mockMvc
        .perform(get("/api/v1/notifications/admin/channel-policies").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
        .andExpect(status().isForbidden());
  }

  @Test
  void upsert_withSuperAdminRole_succeeds() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/notifications/admin/channel-policies/REPAYMENT_DUE")
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("SUPER_ADMIN")))
                        .authorities(SUPER_ADMIN_AUTHORITY))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailEnabled\":true,\"smsEnabled\":false,\"inAppEnabled\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.smsEnabled", equalTo(false)));
  }
}
