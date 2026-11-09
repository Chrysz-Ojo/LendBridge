package com.lendbridge.notification.repository;

import com.lendbridge.notification.model.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
  List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

  List<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID userId);

  Optional<Notification> findByIdAndUserId(UUID id, UUID userId);
}
