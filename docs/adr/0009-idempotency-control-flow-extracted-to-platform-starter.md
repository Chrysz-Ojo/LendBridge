# ADR 0009: Idempotency control-flow extracted to platform-starter

Date: 2026-10-28

## Status

Accepted

## Context

ADR 0004 ("Idempotency-Key handled locally in ledger-service") deliberately built idempotency-key
handling inline rather than as a shared abstraction, naming the explicit trigger for revisiting
that choice: "if the two [services] converge on the same shape, that is the trigger to extract —
not before." Wallet-service's hold creation then did converge on exactly the same shape
(`hold.idempotency_key`, unique column, look-up-before-insert, `DataIntegrityViolationException`
caught and resolved by re-querying) — confirmed by direct comparison of
`JournalEntryServiceImpl.postJournalEntry` and `HoldServiceImpl.createHold`. With payment-service
about to need the same control flow a third and fourth time (`payment_intent`, `payout`), this is
the point ADR 0004 itself named.

## Decision

Extract only the **control flow**, not a generic request/response-caching table — each domain's
response is always reconstructable from its own entity, so there's nothing worth caching
separately. New `platform/starter/src/main/java/com/lendbridge/starter/idempotency/IdempotencyOperations.java`:
a single `@Component` method, `runIdempotently(Supplier<Optional<T>> findByKey, Supplier<T>
createAndPersist) -> T`, that looks up by key first, short-circuits on a hit, otherwise runs the
create-and-persist supplier and resolves a concurrent-race `DataIntegrityViolationException` by
re-querying. Callers keep their own idempotency-key column and `@Transactional` boundary unchanged
— this is a near-zero-diff retrofit, not a new table or a new transaction model.

Retrofitted immediately into both existing call sites
(`JournalEntryServiceImpl`/`HoldServiceImpl`), and used by payment-service from its first commit.

## Consequences

- Any future money-moving-POST endpoint needing idempotency (Loan's eventual commitment/repayment
  endpoints, say) has a tested, shared utility to reach for immediately — no fifth hand-rolled copy.
- This does not solve generic HTTP-level request/response caching (e.g. replaying an identical
  response body byte-for-byte including headers) — it only works because every caller here
  reconstructs its response from a plain database entity. A future need to cache something that
  isn't just "re-fetch an entity and map it" (e.g. a response containing data from an external
  call that can't be cheaply re-derived) would need a different mechanism, not an extension of
  this one.
