package com.lendbridge.starter.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.starter.crypto.UuidV7;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Writes an outbox_event row. Must be called from inside the same @Transactional boundary as the
 * state change it represents (e.g. activating a user), so the write and the state change commit
 * or roll back together.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

  private final OutboxEventRepository repository;
  private final ObjectMapper objectMapper;

  public void write(String aggregateType, String aggregateId, String eventType, Object payload) {
    try {
      String json = objectMapper.writeValueAsString(payload);
      OutboxEvent event =
          OutboxEvent.builder()
              .id(UuidV7.generate())
              .aggregateType(aggregateType)
              .aggregateId(aggregateId)
              .eventType(eventType)
              .payload(json)
              .status(OutboxStatus.PENDING)
              .attempts(0)
              .createdAt(Instant.now())
              .build();
      repository.save(event);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Unable to serialize outbox payload for " + eventType, e);
    }
  }
}
