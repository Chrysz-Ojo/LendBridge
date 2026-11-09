package com.lendbridge.notification.controller;

import com.lendbridge.notification.dto.EventPreferenceRequest;
import com.lendbridge.notification.dto.EventPreferenceResponse;
import com.lendbridge.notification.dto.NotificationResponse;
import com.lendbridge.notification.model.NotificationType;
import com.lendbridge.notification.security.CurrentUser;
import com.lendbridge.notification.service.EventPreferenceService;
import com.lendbridge.notification.service.NotificationService;
import com.lendbridge.starter.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The in-app notification centre (PRD E9-S3): a user's own notifications, with read/unread
 * state, plus the user's per-event-type channel preferences. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationService notificationService;
  private final EventPreferenceService eventPreferenceService;

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<List<NotificationResponse>>> listMine(
      Authentication authentication, @RequestParam(defaultValue = "false") boolean unreadOnly) {
    List<NotificationResponse> notifications =
        notificationService.listForUser(CurrentUser.idFrom(authentication), unreadOnly);
    return ResponseEntity.ok(ApiResponse.success(notifications));
  }

  @PostMapping("/me/{id}/read")
  public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
      Authentication authentication, @PathVariable UUID id) {
    NotificationResponse notification = notificationService.markRead(CurrentUser.idFrom(authentication), id);
    return ResponseEntity.ok(ApiResponse.success(notification));
  }

  @GetMapping("/me/preferences")
  public ResponseEntity<ApiResponse<List<EventPreferenceResponse>>> listMyPreferences(Authentication authentication) {
    List<EventPreferenceResponse> preferences = eventPreferenceService.listMine(CurrentUser.idFrom(authentication));
    return ResponseEntity.ok(ApiResponse.success(preferences));
  }

  @PutMapping("/me/preferences/{eventType}")
  public ResponseEntity<ApiResponse<EventPreferenceResponse>> upsertMyPreference(
      Authentication authentication, @PathVariable NotificationType eventType, @Valid @RequestBody EventPreferenceRequest request) {
    EventPreferenceResponse preference =
        eventPreferenceService.upsert(CurrentUser.idFrom(authentication), eventType, request);
    return ResponseEntity.ok(ApiResponse.success(preference));
  }
}
