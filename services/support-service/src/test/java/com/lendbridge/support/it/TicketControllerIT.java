package com.lendbridge.support.it;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.lendbridge.support.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class TicketControllerIT extends AbstractIntegrationTest {

  private static final GrantedAuthority SUPPORT_AUTHORITY = new SimpleGrantedAuthority("ROLE_SUPPORT");

  @Test
  void createReplyAndResolve_fullFlow() throws Exception {
    UUID userId = UUID.randomUUID();

    String created =
        mockMvc
            .perform(
                post("/api/v1/support/tickets")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"subject":"Withdrawal stuck","category":"PAYMENT","body":"It has been two days."}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", equalTo("OPEN")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String ticketId = JsonPath.read(created, "$.data.id");

    // owner can read their own ticket and its messages
    mockMvc
        .perform(get("/api/v1/support/tickets/" + ticketId).with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/support/tickets/" + ticketId + "/messages").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()", equalTo(1)));

    // a different, non-staff user cannot read it
    mockMvc
        .perform(get("/api/v1/support/tickets/" + ticketId).with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
        .andExpect(status().isUnauthorized());

    // staff replies — ticket moves to IN_PROGRESS
    mockMvc
        .perform(
            post("/api/v1/support/tickets/" + ticketId + "/messages")
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("SUPPORT")))
                        .authorities(SUPPORT_AUTHORITY))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"body":"Looking into it now."}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.authorRole", equalTo("STAFF")));

    mockMvc
        .perform(get("/api/v1/support/tickets/" + ticketId).with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status", equalTo("IN_PROGRESS")));

    // admin queue lists it; non-staff is forbidden from the admin queue
    mockMvc
        .perform(
            get("/api/v1/support/admin/tickets")
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("SUPPORT")))
                        .authorities(SUPPORT_AUTHORITY)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()", equalTo(1)));
    mockMvc
        .perform(get("/api/v1/support/admin/tickets").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isForbidden());

    // staff resolves it
    mockMvc
        .perform(
            patch("/api/v1/support/admin/tickets/" + ticketId + "/status")
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("SUPPORT")))
                        .authorities(SUPPORT_AUTHORITY))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"RESOLVED"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status", equalTo("RESOLVED")));
  }

  @Test
  void getDetail_unknownTicket_returnsNotFound() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/support/tickets/" + UUID.randomUUID())
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
        .andExpect(status().isNotFound());
  }
}
