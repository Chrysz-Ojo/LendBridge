package com.lendbridge.messaging.it;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.messaging.model.Conversation;
import com.lendbridge.messaging.model.ConversationStatus;
import com.lendbridge.messaging.model.Message;
import com.lendbridge.messaging.support.AbstractIntegrationTest;
import com.lendbridge.starter.crypto.UuidV7;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationControllerIT extends AbstractIntegrationTest {

  @Test
  void findMine_returnsConversationsWhereCallerIsBorrowerOrLender() throws Exception {
    UUID callerUserId = UUID.randomUUID();
    Conversation conversation = seedConversation(UUID.randomUUID(), callerUserId);

    mockMvc
        .perform(get("/api/v1/messaging/conversations").with(jwt().jwt(j -> j.subject(callerUserId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(conversation.getId().toString()));
  }

  @Test
  void listMessages_noCursor_returnsLatestPage_andCursorReturnsOnlyNewer() throws Exception {
    UUID borrowerUserId = UUID.randomUUID();
    UUID lenderUserId = UUID.randomUUID();
    Conversation conversation = seedConversation(borrowerUserId, lenderUserId);

    Message first = seedMessage(conversation.getId(), borrowerUserId, "hello");
    Message second = seedMessage(conversation.getId(), lenderUserId, "hi there");

    mockMvc
        .perform(
            get("/api/v1/messaging/conversations/{id}/messages", conversation.getId())
                .with(jwt().jwt(j -> j.subject(borrowerUserId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2));

    mockMvc
        .perform(
            get("/api/v1/messaging/conversations/{id}/messages", conversation.getId())
                .param("afterId", first.getId().toString())
                .with(jwt().jwt(j -> j.subject(borrowerUserId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(second.getId().toString()));
  }

  @Test
  void listMessages_callerNotAParticipant_isUnauthorized() throws Exception {
    Conversation conversation = seedConversation(UUID.randomUUID(), UUID.randomUUID());

    mockMvc
        .perform(
            get("/api/v1/messaging/conversations/{id}/messages", conversation.getId())
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
        .andExpect(status().isUnauthorized());
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

  private Message seedMessage(UUID conversationId, UUID senderUserId, String body) {
    return messageRepository.save(
        Message.builder()
            .id(UuidV7.generate())
            .conversationId(conversationId)
            .senderUserId(senderUserId)
            .body(body)
            .createdAt(Instant.now())
            .build());
  }
}
