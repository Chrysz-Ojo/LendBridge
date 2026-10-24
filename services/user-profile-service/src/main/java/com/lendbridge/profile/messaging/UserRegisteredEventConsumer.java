package com.lendbridge.profile.messaging;

import com.lendbridge.authentication.avro.UserRegistered;
import com.lendbridge.profile.model.ProcessedEvent;
import com.lendbridge.profile.model.UserProfile;
import com.lendbridge.profile.repository.ProcessedEventRepository;
import com.lendbridge.profile.repository.UserProfileRepository;
import com.lendbridge.starter.crypto.UuidV7;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a shell {@link UserProfile} row (userId + email only) when a user registers; every
 * other field is filled in later via {@code PATCH /api/v1/profile/me}. Idempotent against
 * redelivery: the {@code ce_id} CloudEvents header authentication-service's {@code
 * CloudEventHeaders} writes on every record is recorded in {@code processed_event} before acting,
 * so a redelivered record is a no-op (CLAUDE.md: "consumers must be idempotent").
 */
@Component
@RequiredArgsConstructor
public class UserRegisteredEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(UserRegisteredEventConsumer.class);

  private final UserProfileRepository userProfileRepository;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "user.registered", groupId = "user-profile-service")
  @Transactional
  public void onUserRegistered(
      ConsumerRecord<String, UserRegistered> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed user.registered event {}", eventId);
      return;
    }

    UserRegistered payload = record.value();
    UUID userId = UUID.fromString(payload.getUserId());
    if (!userProfileRepository.existsByUserId(userId)) {
      Instant now = Instant.now();
      userProfileRepository.save(
          UserProfile.builder()
              .id(UuidV7.generate())
              .userId(userId)
              .email(payload.getEmail())
              .createdAt(now)
              .updatedAt(now)
              .build());
    }

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
