package com.lendbridge.starter.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class IdempotencyOperationsTest {

  private final IdempotencyOperations idempotencyOperations = new IdempotencyOperations();

  @Test
  void runIdempotently_returnsExisting_withoutCreating() {
    AtomicInteger createCalls = new AtomicInteger();

    String result =
        idempotencyOperations.runIdempotently(
            () -> Optional.of("existing"),
            () -> {
              createCalls.incrementAndGet();
              return "new";
            });

    assertThat(result).isEqualTo("existing");
    assertThat(createCalls.get()).isZero();
  }

  @Test
  void runIdempotently_creates_whenNoneExists() {
    String result = idempotencyOperations.runIdempotently(Optional::empty, () -> "new");

    assertThat(result).isEqualTo("new");
  }

  @Test
  void runIdempotently_resolvesConcurrentRace_byRequeryingOnConstraintViolation() {
    AtomicInteger findCalls = new AtomicInteger();

    String result =
        idempotencyOperations.runIdempotently(
            () -> findCalls.incrementAndGet() == 1 ? Optional.empty() : Optional.of("winner"),
            () -> {
              throw new DataIntegrityViolationException("duplicate key");
            });

    assertThat(result).isEqualTo("winner");
  }

  @Test
  void runIdempotently_rethrowsOriginalViolation_whenRequeryAlsoFindsNothing() {
    DataIntegrityViolationException violation = new DataIntegrityViolationException("duplicate key");

    assertThatThrownBy(
            () ->
                idempotencyOperations.runIdempotently(
                    Optional::empty,
                    () -> {
                      throw violation;
                    }))
        .isSameAs(violation);
  }
}
