package com.lendbridge.notification.repository;

import com.lendbridge.notification.model.EventPreference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventPreferenceRepository extends JpaRepository<EventPreference, UUID> {
  Optional<EventPreference> findByUserIdAndEventType(UUID userId, String eventType);

  List<EventPreference> findByUserIdOrderByEventType(UUID userId);
}
