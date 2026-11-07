package com.lendbridge.notification.it;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.notification.model.Channel;
import com.lendbridge.notification.model.Notification;
import com.lendbridge.notification.model.NotificationStatus;
import com.lendbridge.notification.support.AbstractIntegrationTest;
import com.lendbridge.starter.crypto.UuidV7;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class NotificationControllerIT extends AbstractIntegrationTest {

  @Test
  void listMine_returnsOnlyThisUsersNotificationsMostRecentFirst() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    saveNotification(userId, "Older", Instant.now().minusSeconds(60));
    saveNotification(userId, "Newer", Instant.now());
    saveNotification(otherUserId, "Someone else's", Instant.now());

    mockMvc
        .perform(get("/api/v1/notifications/me").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(2)))
        .andExpect(jsonPath("$.data[0].subject", equalTo("Newer")))
        .andExpect(jsonPath("$.data[1].subject", equalTo("Older")));
  }

  @Test
  void listMine_unreadOnly_excludesAlreadyReadNotifications() throws Exception {
    UUID userId = UUID.randomUUID();
    Notification read = saveNotification(userId, "Read already", Instant.now());
    read.setReadAt(Instant.now());
    notificationRepository.save(read);
    saveNotification(userId, "Still unread", Instant.now());

    mockMvc
        .perform(
            get("/api/v1/notifications/me")
                .param("unreadOnly", "true")
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].subject", equalTo("Still unread")));
  }

  @Test
  void markRead_ownedNotification_setsReadAt() throws Exception {
    UUID userId = UUID.randomUUID();
    Notification notification = saveNotification(userId, "Hello", Instant.now());

    mockMvc
        .perform(post("/api/v1/notifications/me/" + notification.getId() + "/read").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.readAt").exists());
  }

  @Test
  void markRead_notificationBelongingToAnotherUser_returns404() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    Notification notification = saveNotification(ownerId, "Not yours", Instant.now());

    mockMvc
        .perform(post("/api/v1/notifications/me/" + notification.getId() + "/read").with(jwt().jwt(j -> j.subject(requesterId.toString()))))
        .andExpect(status().isNotFound());
  }

  @Test
  void upsertMyPreference_thenListMine_reflectsTheOverride() throws Exception {
    UUID userId = UUID.randomUUID();

    mockMvc
        .perform(
            put("/api/v1/notifications/me/preferences/LOAN_LISTED")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailEnabled\":false,\"smsEnabled\":true,\"inAppEnabled\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.emailEnabled", equalTo(false)));

    mockMvc
        .perform(get("/api/v1/notifications/me/preferences").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].eventType", equalTo("LOAN_LISTED")));
  }

  private Notification saveNotification(UUID userId, String subject, Instant createdAt) {
    return notificationRepository.save(
        Notification.builder()
            .id(UuidV7.generate())
            .userId(userId)
            .eventType("USER_REGISTERED")
            .channel(Channel.IN_APP)
            .subject(subject)
            .body("body")
            .status(NotificationStatus.SENT)
            .createdAt(createdAt)
            .build());
  }
}
