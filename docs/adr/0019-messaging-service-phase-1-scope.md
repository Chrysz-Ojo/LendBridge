# ADR 0019: messaging-service Phase 1 scope — Postgres datastore, committed-only eligibility, home-grown Redis fan-out

Date: 2026-11-12

## Status

Accepted

## Context

Per PRD E9-S1/E9-S5, messaging-service provides real-time chat scoped to a loan request between a
borrower and the lenders eligible to discuss it, with contact-info auto-masking and an abuse-report
flow into an admin queue. Three design points needed deciding before implementation, all confirmed
with the product owner rather than assumed:

1. **Datastore.** The PRD's data-ownership table lists MongoDB for Messaging, but its own "Data and
   storage" table hedges: "Chat messages | MongoDB (or PostgreSQL JSONB if the team prefers fewer
   engines)." Every other service in this repo is PostgreSQL + Liquibase + JPA; nothing anywhere
   uses MongoDB. Introducing it here would add a second datastore technology for no benefit this
   phase's data model actually needs (conversations and messages are plain relational rows; nothing
   calls for a document store).
2. **Chat eligibility.** PRD E9-S1 says chat opens for lenders who "viewed or committed" to a
   request. There is no "viewed" tracking anywhere in the repo — no listing-view event, no
   view-count column, nothing — and building it would mean also touching marketplace-service's
   listing-detail endpoint, out of proportion to this phase.
3. **Transport and fan-out.** PRD's architecture calls for "STOMP over WebSocket" with "Redis
   pub/sub across pods" for the Messaging service specifically. Neither WebSocket/STOMP nor a Redis
   Maven dependency has been used anywhere in this repo before (api-gateway already runs Redis as
   *infrastructure* for its rate limiter, but that's a different module).

This also closes a standing gap: api-gateway was always supposed to do "WebSocket pass-through for
chat" (PRD service catalogue) but had no WebSocket routing at all until this change.

## Decision

**Datastore: PostgreSQL.** `Conversation`, `Message`, `AbuseReport`, and a private
`BorrowerLoanProjection` read model are plain JPA entities with Liquibase migrations, same
package-by-layer shape as every sibling service. No MongoDB dependency is introduced.

**Chat eligibility: "committed" only for Phase 1.** A `Conversation` is created lazily when
`investment.committed` arrives (`lenderUserId` + `loanId`) and a `BorrowerLoanProjection` row
already exists for that loan (built from consuming `loan.listed`, since marketplace-service's own
`ListingResponse` deliberately omits `borrowerUserId` per ADR 0016 and there's no service-to-service
auth in this repo to call another service synchronously for it). "Viewed" eligibility is explicitly
**deferred** — same scope-narrowing pattern as ADRs 0015/0016/0018. Because a loan must already be
`Listed` before any lender can commit to it, `loan.listed` will in practice always have landed before
the matching `investment.committed` — no PENDING-state symmetric-retry machinery (unlike
risk-service's `BorrowerTrustProfile`/`RiskAssessment` pair) is needed here; a missing projection at
commitment time is logged and skipped as an accepted consumer-lag edge case rather than engineered
around.

**Transport: WebSocket/STOMP for live push, REST for everything else, one cursor-based endpoint
doubling as history load and polling fallback.** `GET /api/v1/messaging/conversations/{id}/messages`
takes an optional `afterId`: omitted, it returns the latest page; supplied (the caller's last-seen
message id), it returns anything newer — the same endpoint serves the initial page load and an
explicit client-side polling fallback, with no separate code path to keep in sync. Message ids are
UUIDv7 (repo convention, time-ordered), so `id > afterId` is a correct chronological cursor without
a separate timestamp column. The live-send path is a `@MessageMapping` STOMP handler; JWT auth
happens on the STOMP CONNECT frame's native `Authorization` header (not at the HTTP layer — a
browser WebSocket handshake can't carry a custom header the way a normal REST call does), validated
against the same `JwtDecoder`/jwk-set-uri every other service's REST endpoints already use. Both the
gateway and this service's own `SecurityConfig` permit `/ws/**` at the HTTP layer for this reason.

**Fan-out: a home-grown Redis publish/subscribe bridge, not a full external STOMP broker relay.**
Spring's `enableSimpleBroker` only delivers to sessions connected to the receiving pod.
`enableStompBrokerRelay` expects a real STOMP-speaking broker (e.g. RabbitMQ) — disproportionate
infrastructure for this scale, and nothing else in the repo runs one. Instead: after a message is
persisted, `ConversationEventPublisher` publishes it to a fixed Redis channel
(`messaging:conversation-events`); every pod runs an identical `ConversationEventSubscriber` that,
on receipt, re-broadcasts via `SimpMessagingTemplate` to its own locally-connected STOMP sessions —
including the sending pod, so there's exactly one push code path regardless of which pod received
the client's send. This was built now rather than deferred (unlike the simpler option of shipping a
single-instance-only broker first), per explicit product direction. Redis itself already runs in
`infra/docker-compose`; this is only the first time a *module* other than api-gateway declares it as
a Maven dependency.

**Masking is a new, deliberately simple, regex-based utility** (`ContactInfoMasker`): emails, NG/
E.164-ish phone numbers, bare 10-11 digit runs (account/BVN-shaped numbers), and URLs are replaced
with `[redacted]` before a message is ever persisted or broadcast. No reusable text-scanning
redaction utility existed anywhere in the repo to build on — every prior `mask()` helper (e.g.
wallet-service's account-number mask) truncates one already-identified structured field, not free
text. This is **best-effort pattern matching, not exhaustive PII data-loss-prevention** — a
determined user can still share contact info in ways the patterns don't catch (e.g. spelled-out
digits, unusual phone formats).

**`message.sent` deliberately excludes the message body.** The outbox payload carries
`conversationId`/`messageId`/`senderUserId`/`recipientUserId`/`occurredAtEpochMillis` only, so a
downstream consumer (e.g. a future notification-service hook) can render a generic "you have a new
message" notification and never receive chat content cross-service — least-privilege-in-data, same
principle ADR 0016 applies to not exposing `Listing.borrowerUserId`.

## Consequences

- A lender who merely viewed a listing without committing cannot chat with the borrower this phase —
  a real product gap against the PRD's literal wording, tracked as deferred follow-up work (would
  require a new view-tracking signal, likely sourced from marketplace-service).
- The masking utility will miss creatively-obfuscated contact info; it is a deterrent, not a
  guarantee, and should not be represented to users or compliance as complete PII redaction.
- Two new Maven dependencies enter the repo for the first time in any module other than api-gateway:
  `spring-boot-starter-websocket` and `spring-boot-starter-data-redis`. Both have versions already
  managed transitively via `spring-boot-dependencies` (imported through `platform-bom`), so no new
  version pin was needed in `platform/bom/pom.xml`.
- The Redis fan-out bridge is this repo's first non-Kafka inter-pod messaging mechanism. If a second
  service ever needs the same pattern, it's worth promoting the publish/subscribe bridge shape into
  `platform/starter` rather than reimplementing it — not done preemptively here since messaging-service
  is still the only consumer.
