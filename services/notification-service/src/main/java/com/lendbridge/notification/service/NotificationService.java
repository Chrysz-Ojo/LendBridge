package com.lendbridge.notification.service;

import com.lendbridge.notification.dto.NotificationRequest;
import com.lendbridge.notification.dto.NotificationResponse;
import com.lendbridge.notification.model.Notification;
import java.util.List;
import java.util.UUID;

public interface NotificationService {
  Notification dispatch(NotificationRequest request);

  List<NotificationResponse> listForUser(UUID userId, boolean unreadOnly);

  NotificationResponse markRead(UUID userId, UUID notificationId);
}
