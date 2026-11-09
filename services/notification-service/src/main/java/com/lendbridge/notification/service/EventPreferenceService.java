package com.lendbridge.notification.service;

import com.lendbridge.notification.dto.EventPreferenceRequest;
import com.lendbridge.notification.dto.EventPreferenceResponse;
import com.lendbridge.notification.model.NotificationType;
import java.util.List;
import java.util.UUID;

public interface EventPreferenceService {
  List<EventPreferenceResponse> listMine(UUID userId);

  EventPreferenceResponse upsert(UUID userId, NotificationType eventType, EventPreferenceRequest request);
}
