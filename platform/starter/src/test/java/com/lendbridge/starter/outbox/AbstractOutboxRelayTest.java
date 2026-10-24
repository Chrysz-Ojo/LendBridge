package com.lendbridge.starter.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractOutboxRelayTest {

  @Mock private OutboxEventRepository repository;

  private OutboxEvent succeeding;
  private OutboxEvent failing;

  @BeforeEach
  void setUp() {
    succeeding = newEvent();
    failing = newEvent();
  }

  @Test
  void relay_marksSuccessesPublished_andBumpsAttemptsOnFailure() {
    when(repository.lockNextBatch(100)).thenReturn(List.of(succeeding, failing));
    AtomicInteger publishCalls = new AtomicInteger();
    AbstractOutboxRelay relay =
        new AbstractOutboxRelay(repository) {
          @Override
          protected void publish(OutboxEvent event) throws Exception {
            publishCalls.incrementAndGet();
            if (event == failing) {
              throw new IllegalStateException("boom");
            }
          }
        };

    relay.relay();

    assertThat(publishCalls.get()).isEqualTo(2);
    assertThat(succeeding.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(succeeding.getPublishedAt()).isNotNull();
    assertThat(failing.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(failing.getAttempts()).isEqualTo(1);
  }

  private OutboxEvent newEvent() {
    return OutboxEvent.builder()
        .id(UUID.randomUUID())
        .aggregateType("Test")
        .aggregateId(UUID.randomUUID().toString())
        .eventType("test.event")
        .payload("{}")
        .status(OutboxStatus.PENDING)
        .attempts(0)
        .createdAt(Instant.now())
        .build();
  }
}
