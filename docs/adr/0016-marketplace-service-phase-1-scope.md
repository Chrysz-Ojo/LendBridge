# ADR 0016: marketplace-service Phase 1 scope — Postgres not OpenSearch, listings only

Date: 2026-10-30

## Status

Accepted

## Context

The PRD's service catalogue specifies marketplace-service's read model as "PostgreSQL + OpenSearch
(read model)", and its full scope as listings, search, filters, direct offers and offer comparison
(E3-S2, E4-S2, E5-S1, E7-S1, E7-S2). Building all of that now runs into the same class of gaps
loan-service's Phase 1 already documented (ADR 0015):

- Direct offers (E4-S2) and offer comparison (E7-S2) both end in a borrower accepting an offer,
  which transitions the loan in loan-service — the same cross-service coordination problem as the
  funding saga, deferred for the same reason.
- Risk grade (E5-S2) needs risk-service, which doesn't exist.
- Borrower trust-tier/verification badges (E5-S1, E5-S3) need a lookup against kyc-service for a
  user other than the caller. kyc-service only exposes a self-service "my own" endpoint
  (`GET /api/v1/kyc/verifications/me`), and there is no service-to-service (client-credentials/m2m)
  auth anywhere in this repo (ADR 0008). A Kafka consumer building this read model has no caller
  JWT to forward even if such an endpoint existed.

A narrower, infra-level question: Phase 1's filter set (amount/rate/tenor ranges plus an exact
purpose match) over what will be a small dataset doesn't need OpenSearch's relevance ranking or
full-text search. Adding it now would mean a new docker-compose service, a new client dependency,
and index/mapping/rebuild-from-events machinery for a problem Postgres's own indexed columns
already solve at this scale.

## Decision

Phase 1 is listings only: `GET /api/v1/marketplace/listings` (filtered) and
`GET /api/v1/marketplace/listings/{id}`. No direct offers, no offer comparison, no risk grade, no
trust-tier/verification badges.

The read model lives in PostgreSQL, not OpenSearch — indexed columns on `status`, `amount_kobo`,
`interest_rate_bps` and `tenor_months`, queried via a nullable-parameter JPQL pattern (same
technique used nowhere else in this codebase yet, but a standard way to make a handful of optional
range filters combinable in one query). OpenSearch becomes a documented future migration once real
search needs — full-text, relevance ranking, scale — actually show up; the read/write shape
(rebuild a projection from Kafka events) stays the same either way, so this isn't a dead end.

The read model is built entirely from two events, consumed the same way ledger-service consumes
`payment.succeeded`/`payment.failed` in-process (ADR 0013) — no synchronous call to loan-service or
any other service:

- `loan.listed` (loan-service) — upserts a `Listing` row, keyed on the loan's own id.
- `loan.delisted` (loan-service, new in this change) — published from `LoanServiceImpl.cancel()`
  only when the loan being cancelled was `Listed` (a `Draft`/`Under review` cancellation never had
  a listing to begin with); soft-removes the row (`DELISTED` status, kept not deleted, same
  reasoning as ledger-service keeping a `processed_event` row for an ignored `payment.failed`).

`loan.listed`'s payload also gained a `purpose` field in this change — it didn't carry one before,
and a listing without a purpose fails E3-S2's "shows amount, ... interest rate, tenor, purpose,
risk grade, trust level" display requirement outright. Adding a field to an Avro record already
registered with zero consumers is a backward-compatible, zero-risk change.

`purpose` is stored here as a plain string, not a shared Java enum with loan-service's
`LoanPurpose` — an unrecognized future value from loan-service then just displays as-is instead of
breaking this consumer with an `IllegalArgumentException` on `Enum.valueOf`.

## Consequences

- The next phase of marketplace-service work (direct offers, offer comparison) is gated on
  loan-service gaining a way to accept an offer and transition a loan — the same funding-saga-shaped
  gap ADR 0015 already identified, not a new one.
- Risk grade and trust-tier badges on listings are gated on risk-service existing and/or
  kyc-service gaining a public (or at least cross-service-authenticated) badge-lookup endpoint —
  worth scoping deliberately when it comes up, since "are verification badges public information
  lenders should see" is a product decision, not just a technical one.
- `borrowerUserId` is stored on `Listing` (needed for a future borrower-profile link) but
  deliberately left out of `ListingResponse` for now — nothing in Phase 1 needs a lender to see it.
