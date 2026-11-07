package com.lendbridge.notification.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.notification.dto.EventPreferenceRequest;
import com.lendbridge.notification.dto.EventPreferenceResponse;
import com.lendbridge.notification.model.EventPreference;
import com.lendbridge.notification.model.NotificationType;
import com.lendbridge.notification.repository.EventPreferenceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventPreferenceServiceImplTest {

  @Mock private EventPreferenceRepository repository;

  private EventPreferenceServiceImpl service;
  private UUID userId;

  @BeforeEach
  void setUp() {
    service = new EventPreferenceServiceImpl(repository);
    userId = UUID.randomUUID();
  }

  @Test
  void upsert_noExistingRow_createsNewPreference() {
    when(repository.findByUserIdAndEventType(userId, "LOAN_LISTED")).thenReturn(Optional.empty());
    EventPreferenceRequest request = new EventPreferenceRequest(false, true, true);

    EventPreferenceResponse response = service.upsert(userId, NotificationType.LOAN_LISTED, request);

    ArgumentCaptor<EventPreference> captor = ArgumentCaptor.forClass(EventPreference.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    assertThat(captor.getValue().getEventType()).isEqualTo("LOAN_LISTED");
    assertThat(captor.getValue().isEmailEnabled()).isFalse();
    assertThat(response.getEventType()).isEqualTo("LOAN_LISTED");
    assertThat(response.isEmailEnabled()).isFalse();
  }

  @Test
  void upsert_existingRow_updatesInPlace() {
    EventPreference existing =
        EventPreference.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .eventType("LOAN_LISTED")
            .emailEnabled(true)
            .smsEnabled(true)
            .inAppEnabled(true)
            .createdAt(Instant.now().minusSeconds(3600))
            .build();
    when(repository.findByUserIdAndEventType(userId, "LOAN_LISTED")).thenReturn(Optional.of(existing));
    EventPreferenceRequest request = new EventPreferenceRequest(false, false, true);

    service.upsert(userId, NotificationType.LOAN_LISTED, request);

    assertThat(existing.isEmailEnabled()).isFalse();
    assertThat(existing.isSmsEnabled()).isFalse();
    verify(repository).save(existing);
  }

  @Test
  void listMine_returnsMappedPreferences() {
    EventPreference preference =
        EventPreference.builder().userId(userId).eventType("OTP").emailEnabled(true).smsEnabled(true).inAppEnabled(true).build();
    when(repository.findByUserIdOrderByEventType(userId)).thenReturn(List.of(preference));

    List<EventPreferenceResponse> result = service.listMine(userId);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getEventType()).isEqualTo("OTP");
  }
}
