# ADR 0018: risk-service Phase 1 scope — symmetric retroactive grading, rules-v1 scorecard

Date: 2026-11-12

## Status

Accepted

## Context

Per ADR 0015, loan-service's `UNDER_REVIEW -> LISTED/REJECTED` transition is today a manual
`RISK_OFFICER` review queue, explicitly described as "standing in for the not-yet-built Risk
service." risk-service makes this real: it consumes `kyc.verified`/`kyc.rejected` (borrower trust
tier) and `loan.submitted` (loan terms), computes an A-E grade, and publishes `risk.graded`.

`loan.submitted` and `kyc.verified`/`kyc.rejected` are independent event streams with no guaranteed
ordering and no replay/compaction. A loan can arrive before its borrower's KYC clears — a plausible,
not edge-case, ordering (the borrower may well submit a loan application the moment they finish
onboarding, before KYC adjudication completes). The simpler alternative — do nothing until a trust
profile already exists — would silently and permanently drop that loan, with no mechanism to revisit
it later. A Kafka Streams KTable-KTable join was considered and rejected as infrastructure
disproportionate to this scale: nothing else in this repo uses Kafka Streams, and two plain
correlated tables cover the same requirement.

Real DTI (debt-to-income) needs borrower income, which doesn't exist anywhere in risk-service's
inputs in this scope — fabricating an approximation would present fake data as real, something
ADR 0015/0016 explicitly avoid elsewhere.

A required prerequisite surfaced during this work: `loan-service`'s `loan.submitted` Avro schema
already declared `amountKobo`/`tenorMonths`/`interestRateBps`/`purpose` as required fields, but the
Java producer (`LoanServiceImpl`, `OutboxRelay`) never populated them — the generated Avro builder
would throw at `build()` time, uncaught, since no test exercised the real relay publish path for
this topic. This was fixed as part of this change (not a separate PR) since risk-service's core
grading input is exactly these loan terms.

## Decision

**Data model** — two correlated tables, not a single event-replay mechanism:

- `BorrowerTrustProfile` (PK `user_id`): a per-user projection built from `kyc.verified`/
  `kyc.rejected`, carrying `trust_tier`, `status` (`VERIFIED`/`REJECTED`), `reason`, and
  `source_occurred_at` — the last of which guards against out-of-order redelivery by skipping a
  write if the incoming event is older than what's already stored.
- `RiskAssessment` (PK `loan_id`, reusing the loan's own id, same idiom as marketplace-service's
  `Listing`): one row per loan, with `status` (`PENDING_KYC`/`GRADED`/`KYC_REJECTED`), the graded
  outcome, and a **snapshot** of `amount_kobo`/`tenor_months`/`interest_rate_bps` captured at
  `loan.submitted` time, since that event is never replayable later.

**Processing logic** is fully symmetric regardless of arrival order:
- `loan.submitted` with no existing trust profile -> `PENDING_KYC`. With a `VERIFIED` profile ->
  graded immediately. With a `REJECTED` profile -> `KYC_REJECTED` directly, no event.
- `kyc.verified` -> upserts the trust profile to `VERIFIED`, then grades every `PENDING_KYC`
  assessment for that borrower retroactively (a borrower can have multiple loans submitted before
  KYC clears) — one `risk.graded` event per row graded.
- `kyc.rejected` -> upserts the trust profile to `REJECTED`, flips matching `PENDING_KYC` rows to
  `KYC_REJECTED` with the reason. **No Kafka event is published** for this — there is no "ungraded"
  event in the catalogue, and per PRD E5-S3 only verified borrowers get a usable grade, so this
  stays a local-only audit record.

**Grading rule — `rules-v1`**, a deterministic additive point scorecard (matches the PRD's own
"rules engine first; ML model later"): trust tier is the heaviest single weight (0/1/3 points for
Tier 3/2/1), with amount/tenor/rate bands each contributing 0-2 points (max 6 combined) — so loan
terms alone can outweigh trust tier (an aggressive ask on a thin tier lands at E; a small/short/
cheap loan on the best tier lands at A). The two amount-band thresholds are `@Value`-injected
(`risk.grading.amount-threshold1-kobo`/`-2-kobo`), not hardcoded — same "configurable limits"
convention loan-service already uses. **DTI and "expected default band" are not persisted** for the
reason given above; "expected default band" is exposed only as a non-persisted, grade-derived
display label at the DTO layer, clearly illustrative.

**Endpoints** reuse the existing `RISK_OFFICER` role, no new role invented:
- `GET /api/v1/risk-assessments/{loanId}` — any authenticated caller, grade/status only
  (lender-visible display data, not sensitive).
- `GET /api/v1/risk-assessments/{loanId}/detail` — `@PreAuthorize("hasRole('RISK_OFFICER')")`, full
  detail (trust tier used, loan-term snapshot, rejection reason) — same audit-sensitive-detail
  precedent as loan-service's `LoanReviewController`.

**Event naming**: kept as `risk.graded`/`RiskGraded`, as already scaffolded, even though
`docs/PRD.md`'s service catalogue says `risk.assessed` — that's a documentation drift to fix
separately, not a reason to rename working code.

**Scope**: this round builds risk-service as a complete, independently-working service, plus the
loan-service producer fix above. It does **not** wire loan-service's review queue or
marketplace-service's listings to consume `risk.graded` — those stay deferred, same pattern as ADRs
0015/0016, and become fast follow-up changes once this lands.

## Consequences

- loan-service's manual `RISK_OFFICER` review queue (ADR 0015) and marketplace-service's listings
  still don't consult `risk.graded` yet — a risk officer reviewing a loan today has no automated
  grade in front of them. Wiring that up is explicit follow-up work, not a regression introduced
  here.
- A borrower whose KYC is later re-verified at a different trust tier (if that ever becomes
  possible) does not retroactively re-grade already-`GRADED` assessments — only `PENDING_KYC` rows
  are revisited. This matches today's KYC model (no re-verification flow exists yet) and would need
  revisiting if one is added.
- Two independent read-model projections of trust tier now exist in this codebase (kyc-service's
  own `VerificationCase.trustTier`, and risk-service's `BorrowerTrustProfile`) — the expected shape
  in this architecture (each service owns its own database), not duplication to clean up later.
