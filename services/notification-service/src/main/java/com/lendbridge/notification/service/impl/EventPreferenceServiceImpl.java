package com.lendbridge.notification.service.impl;

import com.lendbridge.notification.dto.EventPreferenceRequest;
import com.lendbridge.notification.dto.EventPreferenceResponse;
import com.lendbridge.notification.model.EventPreference;
import com.lendbridge.notification.model.NotificationType;
import com.lendbridge.notification.repository.EventPreferenceRepository;
import com.lendbridge.notification.service.EventPreferenceService;
import com.lendbridge.starter.crypto.UuidV7;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventPreferenceServiceImpl implements EventPreferenceService {

  private final EventPreferenceRepository repository;

  @Override
  @Transactional(readOnly = true)
  public List<EventPreferenceResponse> listMine(UUID userId) {
    return repository.findByUserIdOrderByEventType(userId).stream().map(this::toResponse).toList();
  }

  @Override
  @Transactional
  public EventPreferenceResponse upsert(UUID userId, NotificationType eventType, EventPreferenceRequest request) {
    Instant now = Instant.now();
    EventPreference preference =
        repository
            .findByUserIdAndEventType(userId, eventType.name())
            .orElseGet(
                () ->
                    EventPreference.builder().id(UuidV7.generate()).userId(userId).eventType(eventType.name()).createdAt(now).build());
    preference.setEmailEnabled(request.getEmailEnabled());
    preference.setSmsEnabled(request.getSmsEnabled());
    preference.setInAppEnabled(request.getInAppEnabled());
    preference.setUpdatedAt(now);
    repository.save(preference);
    return toResponse(preference);
  }


  private EventPreferenceResponse toResponse(EventPreference preference) {
    return EventPreferenceResponse.builder()
        .eventType(preference.getEventType())
        .emailEnabled(preference.isEmailEnabled())
        .smsEnabled(preference.isSmsEnabled())
        .inAppEnabled(preference.isInAppEnabled())
        .updatedAt(preference.getUpdatedAt())
        .build();
  }
}
