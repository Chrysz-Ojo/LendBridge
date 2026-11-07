package com.lendbridge.notification.messaging;

import com.lendbridge.authentication.avro.UserRegistered;
import com.lendbridge.notification.dto.NotificationRequest;
import com.lendbridge.notification.model.Channel;
import com.lendbridge.notification.model.Contact;
import com.lendbridge.notification.model.NotificationType;
import com.lendbridge.notification.model.ProcessedEvent;
import com.lendbridge.notification.repository.ContactRepository;
import com.lendbridge.notification.repository.ProcessedEventRepository;
import com.lendbridge.notification.service.NotificationService;
import java.time.Instant;
import java.util.Map;
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
 * Upserts a {@link Contact} and sends the welcome email when a user registers. Idempotent against
 * redelivery: the {@code ce_id} CloudEvents header authentication-service writes on every record
 * is recorded in {@code processed_event} before acting, so a redelivered record is a no-op
 * (CLAUDE.md: "consumers must be idempotent").
 */
@Component
@RequiredArgsConstructor
public class UserRegisteredEventConsumer {


  private static final Logger log = LoggerFactory.getLogger(UserRegisteredEventConsumer.class);

  private final ContactRepository contactRepository;
  private final ProcessedEventRepository processedEventRepository;
  private final NotificationService notificationService;

  @KafkaListener(topics = "user.registered", groupId = "notification-service")
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
    String email = payload.getEmail();

    Contact contact =
        contactRepository
            .findById(userId)
            .map(existing -> {
                  existing.setEmail(email);
                  existing.setUpdatedAt(Instant.now());
                  return existing;
                })
            .orElseGet(
                () ->
                    Contact.builder()
                        .userId(userId)
                        .email(email)
                        .updatedAt(Instant.now())
                        .build());
    contactRepository.save(contact);

    notificationService.dispatch(
        NotificationRequest.builder()
            .userId(userId)
            .channel(Channel.EMAIL)
            .type(NotificationType.USER_REGISTERED)
            .variables(Map.of("email", email))
            .build());

    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
