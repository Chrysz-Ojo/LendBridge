# ADR 0017: investment-service Phase 1 scope — preferences, commitments, real wallet holds

Date: 2026-10-30

## Status

Accepted

## Context

The PRD's full Investment scope is preference sets (E3-S1), browsing matches (E3-S2, owned by
marketplace-service), diversification (E3-S3), auto-invest (E3-S4), portfolio monitoring (E3-S5)
and committing funds to a loan (E4-S1). Building all of it now runs into gaps already documented
for loan-service (ADR 0015) and marketplace-service (ADR 0016):

- Auto-invest (E3-S4) needs a background worker matching new listings against saved preferences —
  meaningfully larger scope than a Phase 1 ought to take on.
- Portfolio earnings/performance (E3-S5) needs repayment data, which doesn't exist until the
  funding saga does (a loan never reaches `Active` yet).
- Risk-grade preference filtering has nothing to filter against — risk-service doesn't exist.

Unlike marketplace-service's blocked trust-tier lookup, though, **committing funds is
lender-initiated**: the lender's own JWT is already on the HTTP request, so there's no
service-to-service auth gap calling wallet-service's existing
`POST /api/v1/wallets/me/holds`/`DELETE /api/v1/wallets/me/holds/{id}`
(`services/wallet-service/.../controller/HoldController.java`) — the same JWT-forwarding pattern
every other cross-service call in this codebase already uses. **No wallet-service changes are
needed for Phase 1.** Only hold *capture* (converting a hold into an actual debit at disbursement)
is missing, and that's a funding-saga concern, not a commitment-creation one.

## Decision

Phase 1 is preference sets (full CRUD, no risk-grade field) plus commitments: create (places a
real wallet hold), cancel (releases it), and a "my commitments" list. The loan itself never
progresses past `Listed` in loan-service even once 100% committed — that transition, plus
disbursement, stays gated on the funding saga ADR 0015 already deferred.

**This service keeps its own copy of the loan-listing projection** (`LoanListing`: `loanId`,
`borrowerUserId`, `amountKobo`, `active`), built from `loan.listed`/`loan.delisted` the same way
marketplace-service builds its own `Listing` — not a dependency on marketplace-service's database
or API. This is what lets two Phase 1 validations run entirely off this service's own data, with
no synchronous call to loan-service or marketplace-service:

- **Remaining-unfunded amount** (E4-S1): a commitment can't push a loan's total active commitments
  past its listed `amountKobo`.
- **Borrower-exposure cap** (E3-S3, default 20%): a lender's new commitment, added to their
  existing active commitments to the *same* borrower, can't exceed the configured ratio of their
  total active commitments across all borrowers. **A lender's very first active commitment skips
  this check** — it's unavoidably 100% of a zero-item portfolio, and the rule's intent is
  discouraging concentration once there's a portfolio to compare against, not blocking anyone's
  first investment.

**Idempotency**: `Commitment` has its own unique `idempotency_key` column, using
`platform-starter`'s `IdempotencyOperations.runIdempotently` — look up by key first, create
otherwise, the unique constraint as the race backstop. Same shape as wallet-service's own hold
creation.

**Minimum ticket** (E3-S3): configurable, default ₦5,000 (`investment.min-ticket-kobo`, default
500,000 kobo).

A small touch-up to marketplace-service rides along in this same change: `Listing` gains
`fundedAmountKobo`, kept current by two new consumers of `investment.committed`/
`investment.cancelled`. PRD E3-S2 requires showing "amount funded so far" while browsing, and
that display was honestly incomplete without this once commitments exist. marketplace-service
remains a pure read-model consumer — investment-service stays the system of record for the
underlying commitments.

## Consequences

- A lender's commitments are never automatically released if their loan is later cancelled while
  `Funding` (not applicable yet — no loan reaches a funding state in Phase 1) — a real gap once
  the funding saga exists, which that work will need to account for.
- Auto-invest, portfolio earnings, and risk-grade-based preference matching stay deferred until,
  respectively, a background matching worker, the funding saga producing repayment data, and
  risk-service existing.
- Two independent read-model projections of the same `loan.listed`/`loan.delisted` events now
  exist (marketplace-service's `Listing`, investment-service's `LoanListing`) — this is the
  expected shape in this architecture (each service owns its own database), not duplication to
  clean up later.
