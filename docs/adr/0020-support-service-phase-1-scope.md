# ADR 0020: support-service Phase 1 scope — plain document reference, shared category taxonomy, producer-only ticket.updated

Date: 2026-11-12

## Status

Accepted

## Context

Per PRD E9-S2/E9-S4, support-service provides in-app support tickets (category, attachments,
status, threaded replies) and a searchable, admin-managed FAQ page. Unlike risk-service and
messaging-service, this build needed no new infrastructure or libraries and no scope questions put
back to the user — every design choice mirrors a strong, already-confirmed precedent elsewhere in
the repo:

1. **Attachments.** E9-S2 calls for ticket attachments, "same file rules as KYC uploads." Every
   existing cross-reference to a document-service upload elsewhere in the repo (e.g.
   `LoanApplication.agreementDocumentId`) is a bare `UUID` column — no richer type, no live call
   back to document-service to read it.
2. **Category taxonomy.** E9-S2's "category" and E9-S4's FAQ topic grouping both need some
   enumeration. `loan-service`'s `LoanPurpose` already established the exact shape for "pick from a
   list, or free text" fields in this repo: an enum with a trailing `OTHER`, paired with a plain
   nullable `...Detail` string, no conditional "required if OTHER" validation.
3. **`ticket.updated` consumption.** The PRD's event catalogue names `Notification` as this event's
   consumer (for "email copy of replies"), but no such consumer exists anywhere yet, and wiring one
   up is a notification-service change, not a support-service one.

## Decision

**Attachments are a plain nullable `UUID attachmentDocumentId` on `Ticket`.** No Feign/RestClient
call to document-service is made from this service in Phase 1 — it only stores the reference to an
upload that already happened, same as how loan-service's own agreement reference works.

**`TicketCategory` (`ACCOUNT, LOAN, PAYMENT, KYC, TECHNICAL, OTHER`) is shared between `Ticket` and
`Faq`** rather than maintaining two separate taxonomies — tickets and FAQs cover the same topic
space, and a second enum would be pure duplication. `Ticket.categoryDetail` follows `LoanPurpose`'s
exact pattern: optional free text, no cross-field validation requiring it only when `category ==
OTHER`.

**Ticket access is "owner or `SUPPORT` staff," checked inline rather than as two separate routes.**
`GET`/reply endpoints accept either the ticket's own owner or anyone holding the `SUPPORT` role
(`CurrentUser.isSupportStaff(Authentication)`, mirroring messaging-service's
participant-or-staff shape); the admin queue listing and status-transition endpoints are
staff-only via plain `@PreAuthorize("hasRole('SUPPORT')")`, same role-reuse precedent as
messaging-service's abuse-report queue and notification-service's `SUPER_ADMIN`-gated channel
policies.

**FAQ reads are public; FAQ writes are `SUPPORT`-only.** `GET /api/v1/support/faqs` requires no
authentication at all (permitted in `SecurityConfig`, same idiom as the swagger-path permits) —
PRD E9-S4 describes a help-center-style page, not an authenticated feature. Search is a simple
case-insensitive substring match over question/answer done in the service layer (not a derived
repository query spanning AND/OR across two fields, nor native SQL `ILIKE`) — the expected FAQ
dataset is small enough that fetching all published rows and filtering in Java was judged simpler
than the awkward Spring Data method-name syntax a combined derived query would need.

**`ticket.updated` is metadata-only** (`ticketId`, `userId`, `status`, `lastMessageAuthorRole`,
`occurredAtEpochMillis`) and fires on every reply and every staff status change — never the message
body, same least-privilege-in-data precedent as messaging-service's `message.sent`. Wiring
notification-service to actually consume it and send the PRD's "email copy of replies" is
explicitly **deferred** to a fast-follow change in notification-service itself, same producer-only
pattern already accepted for risk-service's `risk.graded` and messaging-service's `message.sent`.

**No new Maven dependencies.** Same REST+JPA+Kafka+Liquibase baseline as every other
non-WebSocket service. This service has zero Kafka *consumers* — nothing in its scope reacts to
another service's events — so there's no `ProcessedEvent`/idempotency table; the `application.yml`
still declares a (gentically unused) consumer block purely to keep config shape consistent with
every sibling service.

## Consequences

- A ticket's attachment is a bare id with no live validation that the referenced document actually
  exists, belongs to the ticket's owner, or has finished virus-scanning — the same trust boundary
  loan-service already accepts for its own agreement-document reference. If support-service ever
  needs to render attachment metadata (filename, scan status) directly, a Feign client to
  document-service (mirroring kyc-service's `DocumentClient`) would need to be added then.
- The PRD's "email copy of replies" is not yet real — `ticket.updated` is produced but nothing
  consumes it. This is a known, tracked gap, not an oversight.
- FAQ search is a naive in-memory substring filter. It will not scale gracefully to a very large FAQ
  set, but the PRD's own stance on search infrastructure elsewhere in this repo (avoiding OpenSearch
  except where marketplace listings genuinely need it) supports keeping this simple until there's
  evidence it needs to be anything more.
