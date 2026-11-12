# ADR 0021: audit-service Phase 1 scope — generic topic-pattern sink, passive consumer, Postgres-only

Date: 2026-11-12

## Status

Accepted

## Context

Per PRD E11-S4 and the Auditability NFR, audit-service should record "every admin action and
every state change on users, loans and money... with actor, time, before and after." Unlike every
service built so far, audit-service publishes nothing — per the PRD's service catalogue it's a
pure sink, and that shape forces two decisions no prior service in this repo has needed to make:

1. **How to consume 21-and-growing unrelated topics without copying every other service's `.avsc`
   schema in.** Every other Kafka-consuming service (notification, risk, messaging) copies the
   specific schema(s) it cares about and runs one typed `@KafkaListener` per topic. That doesn't
   scale here — audit-service's whole purpose is to see every topic, and the list only grows as
   new services ship.
2. **How much of "actor, time, before and after" is actually achievable today.** Checking every
   `@PreAuthorize`-gated endpoint across the repo (role assignment, channel-policy edits,
   reconciliation runs, KYC/loan review decisions, FAQ CRUD, abuse-report resolution) confirmed: no
   service publishes a dedicated "admin action taken" event, and several of those admin writes
   publish nothing to Kafka at all. The domain events that do exist (`loan.listed`, `ticket.updated`,
   etc.) carry only the *new* state — no previous-value field exists on any payload DTO in the
   repo, and in at least one case (`TicketServiceImpl.updateStatus`) the old value isn't even kept
   in a local variable before being overwritten.

Both of these were confirmed with the user before finalizing scope, rather than assumed.

## Decision

**One listener, subscribed by topic pattern, consuming generic Avro.**
`@KafkaListener(topicPattern = "^[a-z]+(_[a-z]+)*\\.[a-z_.]+$", groupId = "audit-service")`
matches every existing topic's `noun.verb` naming convention without naming any of them
individually, and picks up any future service's new topics with zero code change here.
`spring.kafka.consumer.properties.specific.avro.reader` is `false` — the inverse of every sibling
service — so the deserializer hands back a `GenericRecord` instead of a generated class.
`audit-service/src/main/avro` doesn't exist; there's nothing to generate. The payload is stored as
Avro's own `GenericData` JSON rendering (`GenericRecord#toString()`), not a hand-rolled encoder.
`occurredAt` is read from the payload's own `occurredAtEpochMillis` field when present (checked via
`Schema#getField`, not assumed), falling back to the Kafka record's broker timestamp otherwise.

**Phase 1 is a passive consumer only — admin-action capture is explicitly deferred.** audit-service
records whatever already flows through Kafka today (loan lifecycle events, KYC decisions'
*outcome*, payments, wallet holds, etc.). It does **not** attempt to capture per-admin-action
actor/before/after detail, because that data doesn't exist anywhere yet. Closing that gap for real
means instrumenting the write paths of authentication-service (role assignment), notification-service
(channel-policy edits), payment-service (reconciliation runs), kyc-service and loan-service (review
decisions — including their reject paths, which today publish nothing), support-service (FAQ CRUD),
and messaging-service (abuse-report resolution) to each emit a generic "admin action" signal with
actor and before/after values. That is real cross-service scope, tracked here as a named follow-up,
not built in this change.

**Storage is plain PostgreSQL, append-only — no S3/object-lock archival this round.** The PRD ties
audit-service to a 5-year retention requirement with object-storage archival ("object lock"), and
document-service already has an S3 dependency/pattern that could be mirrored. Deferred anyway: the
append-only Postgres table is useful on its own in dev/staging today, and archival is a compliance
concern best tackled once there's a real retention policy to build against, not speculatively now.

**Read access is `SUPER_ADMIN`-only**, not `SUPPORT`/`FINANCE`/any other staff role — this is the
single most sensitive read surface in the system (a cross-service history of state changes), so it
sits above every other role-gated endpoint built so far rather than following the "reuse whichever
role already fits" pattern used everywhere else.

## Consequences

- audit-service today is a faithful record of *domain* state changes (what happened), not of *who
  decided it should happen or what it looked like before* — the PRD's "actor... before and after"
  wording is only partially satisfied. This is a known, named gap, not an oversight: the six
  services that would need instrumenting are listed above for whoever picks it up.
- The topic-pattern/`GenericRecord` design means a malformed or unexpectedly-shaped future topic
  can't break this service's deserialization in a type-specific way (there's no generated class to
  mismatch) — but it also means nothing here validates a payload's shape; `audit-service` stores
  whatever arrives, as-is.
- No retention/expiry policy or archival job exists yet. The `audit_record` table will grow
  unboundedly until a follow-up addresses the PRD's 5-year/object-lock requirement.
- Because this service has no outbox (it publishes nothing), the per-sibling
  `platform.outbox.*`/`ce-source` config block is absent from `application.yml` — the first service
  built this session without it.
