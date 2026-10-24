package com.lendbridge.profile.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.profile.avro.ProfileUpdated;
import com.lendbridge.profile.model.OutboxEvent;
import com.lendbridge.profile.model.OutboxStatus;
import com.lendbridge.profile.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the outbox table with a {@code FOR UPDATE SKIP LOCKED} batch select so multiple replicas
 * of this service can run the relay concurrently without a distributed lock (ShedLock is
 * deliberately not used here, per the plan).
 */
@Component
@RequiredArgsConstructor
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
  private static final int BATCH_SIZE = 100;

  private final OutboxEventRepository repository;
  private final ObjectMapper objectMapper;
  private final KafkaTemplate<String, Object> kafkaTemplate;

  @Scheduled(fixedDelayString = "${profile.outbox.relay-interval-ms:2000}")
  @Transactional
  public void relay() {
    List<OutboxEvent> batch = repository.lockNextBatch(BATCH_SIZE);
    for (OutboxEvent event : batch) {
      try {
        publish(event);
        event.setStatus(OutboxStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
      } catch (Exception e) {
        event.setAttempts(event.getAttempts() + 1);
        log.error("Failed to publish outbox event {} ({})", event.getId(), event.getEventType(), e);
      }
    }
  }

  private void publish(OutboxEvent event) throws Exception {
    switch (event.getEventType()) {
      case KafkaTopics.PROFILE_UPDATED -> publishProfileUpdated(event);
      default -> throw new IllegalStateException("Unknown outbox event type: " + event.getEventType());
    }
  }

  private void publishProfileUpdated(OutboxEvent event) throws Exception {
    ProfileUpdatedPayload payload =
        objectMapper.readValue(event.getPayload(), ProfileUpdatedPayload.class);
    ProfileUpdated avroValue =
        ProfileUpdated.newBuilder()
            .setUserId(payload.getUserId())
            .setRoles(payload.getRoles())
            .setUpdatedAtEpochMillis(payload.getUpdatedAtEpochMillis())
            .build();
    ProducerRecord<String, Object> record =
        new ProducerRecord<>(
            KafkaTopics.PROFILE_UPDATED,
            null,
            event.getAggregateId(),
            avroValue,
            CloudEventHeaders.forEvent(event));
    kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
  }
}
