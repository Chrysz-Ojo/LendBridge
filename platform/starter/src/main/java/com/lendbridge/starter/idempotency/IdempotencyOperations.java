package com.lendbridge.starter.idempotency;

import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * The repeated shape behind every "Idempotency-Key header" endpoint in this codebase (ledger-service's
 * journal-entry posting, wallet-service's hold creation): look up an existing row by idempotency
 * key first; if found, replay it unchanged — PRD/CLAUDE.md's "duplicates return the original
 * response" rule, not a different status for a retry. Otherwise create it, with the row's own
 * unique constraint as the real backstop against a concurrent duplicate request racing the check
 * (caught here and resolved by re-querying, rather than propagating a constraint-violation error).
 * Callers keep their own idempotency-key column and {@code @Transactional} boundary — this only
 * extracts the control flow, not a generic response-caching table.
 */
@Component
public class IdempotencyOperations {

  public <T> T runIdempotently(Supplier<Optional<T>> findByKey, Supplier<T> createAndPersist) {
    Optional<T> existing = findByKey.get();
    if (existing.isPresent()) {
      return existing.get();
    }
    try {
      return createAndPersist.get();
    } catch (DataIntegrityViolationException e) {
      return findByKey.get().orElseThrow(() -> e);
    }
  }
}
