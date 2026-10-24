package com.lendbridge.starter.outbox;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the outbox table with a {@code FOR UPDATE SKIP LOCKED} batch select so multiple replicas
 * of a service can run the relay concurrently without a distributed lock (ShedLock is
 * deliberately not used here). Subclasses only need to implement {@link #publish(OutboxEvent)}
 * with their own event-type dispatch (which Avro class to build, which topic to send to) — the
 * polling/retry loop itself is identical across every service.
 */
public abstract class AbstractOutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(AbstractOutboxRelay.class);
  private static final int BATCH_SIZE = 100;

  private final OutboxEventRepository repository;

  protected AbstractOutboxRelay(OutboxEventRepository repository) {
    this.repository = repository;
  }

  @Scheduled(fixedDelayString = "${platform.outbox.relay-interval-ms:2000}")
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

  protected abstract void publish(OutboxEvent event) throws Exception;
}
