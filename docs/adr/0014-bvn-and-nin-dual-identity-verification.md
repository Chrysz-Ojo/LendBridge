# ADR 0014: BVN and NIN dual identity verification, three-provider routing, and deferred live document preview for KYC officers

Date: 2026-10-28

## Status

Accepted

## Context

KYC Service is next in the build order, slotted in ahead of Risk/Marketplace/Investment/Loan since
those services and the Loan funding saga all assume a verified, trust-tiered borrower. Per the PRD,
KYC Service owns BVN/ID/liveness/bank-statement checks, computes a trust tier (1-3) that gates
borrowing/lending limits, and is the gate for listing a borrower on the marketplace (E5-S3).

The user asked to also verify **NIN** (National Identification Number) alongside BVN — this extends
the PRD's literal BVN-only text (E2-S2, E5-S3, the KYC service responsibility row, and the KYC data
ownership row), the same kind of deliberate extension ADR 0001 recorded for switching to Liquibase.

Real provider adapters: **Smile Identity, Dojah, and Prembly** (confirmed with the user), mirroring
payment-service's three-adapter, two-way-routed `PaymentProviderRouter` precedent (ADR 0010)
exactly, with a fake implementation for local dev/CI per CLAUDE.md's external-provider rule.

## Decision

**NIN as a second identity check.** `NinResult` is identical in shape to `BvnResult` (ciphertext +
salted hash + match booleans + outcome), both tables keyed by `verification_case_id`. BVN is
required for every case; NIN is optional and only raises the achievable trust tier (Tier 1 = BVN
`PASSED` + face-match; Tier 2 adds NIN `PASSED`; Tier 3 adds liveness-passed + a passed bank
statement check). Only three provider-result tables exist, not five: Smile ID/Dojah/Prembly all
bundle liveness and selfie-vs-ID face-match into one biometric job, so `LivenessResult` alone covers
both the "Liveness" and "ID" badges from E5-S3.

**Outcome routing is a flat aggregation across every attempted check**, not scoped narrowly to
whichever checks define the tier the borrower happens to reach:

1. No `MISMATCH`/`FAILED` anywhere → case auto-`VERIFIED`, tier computed from whatever passed.
2. Any `MISMATCH`, no `FAILED` → case stays `PENDING`, `review_required=true`, shown in the KYC
   officer queue. `NEEDS_MORE_INFO` is reserved for an explicit officer decision
   (`REQUEST_INFO`) — it is never set automatically.
3. Any `FAILED` (a definitive provider negative, e.g. the BVN/NIN does not exist) → case
   auto-`REJECTED` with a system reason, skipping the queue entirely. The borrower may start a
   fresh case afterward since `REJECTED` is not an "open" status.
4. A transient failure (both configured providers down, document-service unreachable, a document
   not `CLEAN` or the wrong type) is **never** recorded as a verdict — it surfaces as
   `DependencyUnavailableException`/`UnprocessableEntityException` and leaves the case untouched.
   Recording a provider outage as `FAILED` would reject legitimate users during an outage, a risk
   the PRD itself names.

**No `Idempotency-Key` header** on `POST /api/v1/kyc/verifications` — CLAUDE.md scopes that rule to
money-moving POSTs. `IdempotencyOperations.runIdempotently` is reused instead, keyed by the
JWT-derived `user_id` against a partial unique index (`status IN ('PENDING', 'NEEDS_MORE_INFO')`) on
`verification_case` — a duplicate `POST` while a case is still open just replays that case.

**Router: primary/secondary only**, exactly like `PaymentProviderRouter`. A `List<VerificationProvider>`
is injected; primary/secondary are resolved from `kyc.provider.primary`/`secondary`; each of the
three check verbs (`checkBvn`, `checkNin`, `checkLiveness`) is wrapped in
`@CircuitBreaker(name="kycProviderPrimary")` + `@Retry`, falling back to the secondary provider on
failure. If the secondary also fails, that is wrapped as `DependencyUnavailableException` (routing
rule 4) rather than letting a raw provider SDK exception escape as an unhandled 500. The third
configured provider stays reachable via direct lookup but isn't in the auto-failover chain — one
real precedent in this repo (payment-service) for a 3-adapter/2-way-routed shape, not a fancier
N-way router.

**Role-based access.** `ReviewController` carries `@PreAuthorize("hasRole('KYC_OFFICER')")` per
method, reusing the precedent payment-service's `ReconciliationController` established with
`hasRole('FINANCE')`. `VerificationController` only requires an authenticated caller.

**Deferred live document preview for KYC officers — a known gap, documented not papered over.**
There is no service-to-service (M2M) auth mechanism anywhere in this repo yet (the same wall ADR
0008 named for wallet-service/ledger-service), so `DocumentGateway` can only fetch a document under
the *caller's own* JWT, forwarded through `DocumentFeignConfig` — exactly like wallet-service's
`LedgerGateway`/`LedgerFeignConfig`. A KYC officer's own JWT is not the borrower's, so
`ReviewController`'s queue cannot live-re-fetch a borrower's ID/selfie/bank-statement documents.
Instead, `VerificationCase.document_snapshot` captures each document's id, type, scan status and
checksum as JSON at check time, and the review queue serves that snapshot. Live document preview
for officers is a follow-up once M2M auth exists platform-wide.

## Consequences

- A fourth real KYC provider (or a replacement for one of the three) adds one adapter package and
  a config entry — no change to the port, the router, or any service-layer code, same as ADR 0010's
  consequence for `PaymentProvider`.
- The three real adapters (Smile Identity, Dojah, Prembly) are modeled from general knowledge of
  each vendor's public API shape, not confirmed against live/current docs the way payment-service's
  three PSP adapters were (ADR 0010 states those were "confirmed via direct review of each
  provider's current docs" — this session did not have that same direct access for the KYC
  vendors). They compile, satisfy `VerificationProvider`, and have narrow unit tests against fixed
  sample payloads, but their exact request/response field names should be verified against each
  vendor's live documentation before any real sandbox credentials are wired up. `FakeVerificationProvider`
  is what every test and CI run exercises, same as `FakePaymentProvider`.
- A KYC officer reviewing a queued case sees only the metadata snapshot, not a live document
  preview, until M2M auth is built — a real workflow limitation, not an oversight.
- `NEEDS_MORE_INFO` still counts as an "open" case for the idempotency partial-unique-index check,
  and there is no dedicated resubmission endpoint yet for a borrower whose case was sent back for
  more information — a borrower in that state is unblocked only once a KYC officer later approves
  or rejects the same case. Tracked as a follow-up alongside the M2M-auth gap above.
