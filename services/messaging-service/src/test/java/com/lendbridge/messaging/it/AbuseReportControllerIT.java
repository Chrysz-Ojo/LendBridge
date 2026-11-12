package com.lendbridge.messaging.it;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.messaging.model.AbuseReport;
import com.lendbridge.messaging.model.AbuseReportStatus;
import com.lendbridge.messaging.model.Conversation;
import com.lendbridge.messaging.model.ConversationStatus;
import com.lendbridge.messaging.support.AbstractIntegrationTest;
import com.lendbridge.starter.crypto.UuidV7;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Role-gating is a Spring-context-level concern, same reasoning as every sibling service's own
 * role-gated-endpoint IT (e.g. notification-service's {@code ChannelPolicyControllerIT}). */
class AbuseReportControllerIT extends AbstractIntegrationTest {

  private static final GrantedAuthority SUPPORT_AUTHORITY = new SimpleGrantedAuthority("ROLE_SUPPORT");

  @Test
  void file_participantFilesReport_succeeds() throws Exception {
    UUID borrowerUserId = UUID.randomUUID();
    Conversation conversation = seedConversation(borrowerUserId, UUID.randomUUID());

    mockMvc
        .perform(
            post("/api/v1/messaging/conversations/{id}/reports", conversation.getId())
                .with(jwt().jwt(j -> j.subject(borrowerUserId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"inappropriate content\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("OPEN"));
  }

  @Test
  void listOpenReports_withoutSupportRole_isForbidden() throws Exception {
    mockMvc
        .perform(get("/api/v1/messaging/admin/reports").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
        .andExpect(status().isForbidden());
  }

  @Test
  void listOpenReports_withSupportRole_succeeds() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/messaging/admin/reports")
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("SUPPORT")))
                        .authorities(SUPPORT_AUTHORITY)))
        .andExpect(status().isOk());
  }

  @Test
  void resolve_withSupportRole_marksResolved() throws Exception {
    AbuseReport report = seedOpenReport();

    mockMvc
        .perform(
            patch("/api/v1/messaging/admin/reports/{id}/resolve", report.getId())
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("SUPPORT")))
                        .authorities(SUPPORT_AUTHORITY)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("RESOLVED"));
  }

  private Conversation seedConversation(UUID borrowerUserId, UUID lenderUserId) {
    Instant now = Instant.now();
    return conversationRepository.save(
        Conversation.builder()
            .id(UuidV7.generate())
            .loanId(UUID.randomUUID())
            .borrowerUserId(borrowerUserId)
            .lenderUserId(lenderUserId)
            .status(ConversationStatus.OPEN)
            .createdAt(now)
            .updatedAt(now)
            .build());
  }

  private AbuseReport seedOpenReport() {
    Conversation conversation = seedConversation(UUID.randomUUID(), UUID.randomUUID());
    return abuseReportRepository.save(
        AbuseReport.builder()
            .id(UuidV7.generate())
            .conversationId(conversation.getId())
            .reporterUserId(conversation.getBorrowerUserId())
            .reason("spam")
            .status(AbuseReportStatus.OPEN)
            .createdAt(Instant.now())
            .build());
  }
}
