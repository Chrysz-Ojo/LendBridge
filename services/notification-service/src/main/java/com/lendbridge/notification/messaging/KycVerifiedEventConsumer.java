package com.lendbridge.notification.messaging;

import com.lendbridge.kyc.avro.KycVerified;
import com.lendbridge.notification.dto.NotificationRequest;
import com.lendbridge.notification.model.Channel;
import com.lendbridge.notification.model.NotificationType;
import com.lendbridge.notification.model.ProcessedEvent;
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

/** Idempotent against redelivery via {@code processed_event}, same pattern as
 * {@link UserRegisteredEventConsumer}. */
@Component
@RequiredArgsConstructor
public class KycVerifiedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(KycVerifiedEventConsumer.class);

  private final ProcessedEventRepository processedEventRepository;
  private final NotificationService notificationService;

  @KafkaListener(topics = "kyc.verified", groupId = "notification-service")
  @Transactional
  public void onKycVerified(
      ConsumerRecord<String, KycVerified> record, @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed kyc.verified event {}", eventId);
      return;
    }

    KycVerified payload = record.value();
    UUID userId = UUID.fromString(payload.getUserId());

    notificationService.dispatch(
        NotificationRequest.builder()
            .userId(userId)
            .channel(Channel.EMAIL)
            .type(NotificationType.KYC_VERIFIED)
            .variables(Map.of("trustTier", payload.getTrustTier().toString()))
            .build());

    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
