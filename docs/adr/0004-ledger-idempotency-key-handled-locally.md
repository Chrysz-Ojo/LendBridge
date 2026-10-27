# ADR 0004: Idempotency-Key handled locally in ledger-service

Date: 2026-10-27

## Status

Accepted

## Context

CLAUDE.md and the PRD both require "every money-moving POST" to carry an `Idempotency-Key`
header, with duplicates returning the original response. No handling for this existed anywhere
in the repo before ledger-service (confirmed: zero matches for "idempotency-key" in any `.java`
file). CLAUDE.md's repo-layout description already lists "idempotency" as something
`platform-starter` owns, but that line is aspirational — nothing backing it has been built yet.

This repo has a clear precedent for this situation: build a thing inline in the first service
that needs it, and extract it to `platform-starter` only once a second real use case shows up
exactly what the extraction should look like. `FieldCipher`/`EncryptedStringConverter` followed
this path — they stayed in authentication-service alone until user-profile-service became the
second real use case and the extraction happened then, not before.

## Decision

ledger-service implements Idempotency-Key handling itself, scoped to its own
`POST /journal-entries` endpoint:

- `journal_entry.idempotency_key` is a `NOT NULL`, unique column.
- `JournalEntryServiceImpl.postJournalEntry(...)` looks up by `idempotencyKey` first, inside the
  same `@Transactional` method; a hit short-circuits all validation and persistence and returns
  the existing entry's response, unchanged.
- The unique constraint is the real backstop against a concurrent duplicate request racing the
  check: a `DataIntegrityViolationException` on save is caught, the row is re-queried by key, and
  that result is returned instead of propagating an error.

No new `platform-starter` abstraction is added for this.

## Consequences

- Payment Service is the PRD's other named user of Idempotency-Key. Whoever builds it should
  look at this implementation first rather than invent a divergent mechanism; if the two
  converge on the same shape, that is the trigger to extract a shared abstraction — not before.
- If Payment Service (or any other money-moving service) needs meaningfully different semantics
  (e.g. caching a full response body rather than just replaying a re-fetched entity), this
  decision should be revisited rather than forcing a shared abstraction to fit both.
