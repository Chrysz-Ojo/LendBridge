package com.lendbridge.notification.repository;

import com.lendbridge.notification.model.Channel;
import com.lendbridge.notification.model.NotificationTemplate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
  Optional<NotificationTemplate> findFirstByEventTypeAndChannelAndActiveTrueOrderByVersionDesc(
      String eventType, Channel channel);
}
