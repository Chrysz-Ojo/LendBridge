# ADR 0015: loan-service Phase 1 scope — no funding saga, borrower-triggered agreement, PDFBox

Date: 2026-10-30

## Status

Accepted

## Context

The PRD's full loan lifecycle (Draft through Active/Repaid/Defaulted) is driven by a funding saga
orchestrated by the Loan Service across Investment, Wallet, Ledger and Payment. Building that saga
now would require capabilities that don't exist anywhere in this repo:

- `wallet-service`'s `HoldService` only creates and releases holds — there is no capture/convert-
  to-debit method the saga needs to move a lender's reserved funds.
- `ledger-service` has no escrow account — only a hardcoded `PAYMENT_CLEARING_NGN` clearing account
  used internally by its own Kafka consumer (ADR 0013).
- `investment-service` and `marketplace-service` don't exist, so there is no real lender commitment
  or offer to fund a loan against.
- No saga/orchestration framework exists anywhere in the codebase — only doc comments referencing
  the *future* Loan-service saga.

Building all of that inside this change would mean extending two other services' bounded contexts
and partially building a third, breaking from how every prior service (kyc, payment, wallet,
ledger) was built in isolation, only ever reading another service's already-existing API.

A second, narrower gap: `document-service` scopes every document strictly to the uploading user's
own id (`DocumentController`), and there is no service-to-service (client-credentials/m2m) auth
anywhere in this repo (ADR 0008 documents this directly). If a risk officer's loan-approval call
triggered agreement generation, it would forward the *officer's* JWT, attributing the document to
the wrong user.

## Decision

Phase 1 covers everything in loan-service's own bounded context that doesn't depend on the missing
capabilities above:

- Application lifecycle `Draft -> Under review -> Listed | Rejected`, plus `Cancelled` from
  `Draft`/`Under review`/`Listed`. No `Funding`, `Funded`, `Active`, `Repaid`, `Defaulted`,
  `Written off` or `Expired` states yet.
- A repayment-schedule *preview* calculator (amortisation formula, banker's rounding, remainder on
  the final instalment) — computed on demand, not persisted, since there's no real disbursement
  date until funding exists.
- A manual review queue gated on a `RISK_OFFICER` role, standing in for the not-yet-built Risk
  service — mirrors kyc-service's `ReviewController`/`ReviewServiceImpl` pattern exactly.
- Agreement PDF generation is **borrower-triggered**, not automatic on officer approval: once a
  loan is `Listed`, the borrower calls `POST /api/v1/loans/{id}/agreement` themselves, so their own
  JWT (not the officer's) is what gets forwarded to document-service. This also means Phase 1 only
  generates the document — e-signature/OTP acceptance (E7-S3) ties to accepting a lender offer,
  which doesn't exist yet.
- PDF rendering uses Apache PDFBox (Apache 2.0), added directly to `services/loan-service/pom.xml`
  with its own `<version>` (not in the shared BOM, since it's loan-service-specific) — same pattern
  as api-gateway's one-off `nimbus-jose-jwt` dependency.
- The upload call to document-service uses a plain `RestClient`, not Feign: Feign needs an extra
  multipart encoder library (`feign-form`) that isn't in the approved stack, and no service in this
  repo uploads a file via Feign today. `RestClient` ships with `spring-boot-starter-web` already on
  the classpath.

Funding, disbursement, lender commitments/offers, repayment recording, late fees and default
handling are explicitly deferred — same pattern as ADR 0011 (direct-debit deferred) and ADR 0012
(reconciliation on-demand only).

## Consequences

- The next phase of loan-service work is gated on investment-service and marketplace-service
  existing, and on wallet-service/ledger-service gaining hold-capture and escrow support — at which
  point `LoanStatus` gains its remaining states and the funding saga gets built as its own
  follow-up, rather than a half-finished, untested code path shipped now.
- The agreement PDF generated in Phase 1 states loan terms only (amount, rate, tenor) — it does not
  pull borrower name/address from user-profile-service. A richer template is a future enhancement
  once the funding/signing flow exists to actually need it.
- `document-service`'s `DocumentType.LOAN_AGREEMENT` enum value already anticipated this use case.
