package com.lendbridge.reporting.messaging;

import com.lendbridge.authentication.avro.UserRegistered;
import com.lendbridge.reporting.model.ProcessedEvent;
import com.lendbridge.reporting.model.UserSignupFact;
import com.lendbridge.reporting.repository.ProcessedEventRepository;
import com.lendbridge.reporting.repository.UserSignupFactRepository;
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

/** Idempotent against redelivery via {@code processed_event}, same pattern as every other
 * consumer in this repo. */
@Component
@RequiredArgsConstructor
public class UserRegisteredEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(UserRegisteredEventConsumer.class);

  private final ProcessedEventRepository processedEventRepository;
  private final UserSignupFactRepository userSignupFactRepository;

  @KafkaListener(topics = "user.registered", groupId = "reporting-service")
  @Transactional
  public void onUserRegistered(
      ConsumerRecord<String, UserRegistered> record, @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = idFor(record, ceIdHeader);
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed user.registered event {}", eventId);
      return;
    }

    UserRegistered payload = record.value();
    userSignupFactRepository.save(
        UserSignupFact.builder()
            .id(UuidV7.generate())
            .userId(UUID.fromString(payload.getUserId().toString()))
            .occurredAt(Instant.ofEpochMilli(payload.getRegisteredAtEpochMillis()))
            .build());

    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }

  private static String idFor(ConsumerRecord<String, ?> record, byte[] ceIdHeader) {
    String ceId = ceIdHeader != null ? new String(ceIdHeader) : null;
    return record.topic() + ":" + (ceId != null ? ceId : String.valueOf(record.offset()));
  }
}
