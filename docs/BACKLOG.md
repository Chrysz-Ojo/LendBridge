# Backlog: findings from the 2026-11-13 PRD/fintech-standards audit

Source: a full audit comparing `docs/PRD.md`, all ADRs, and the actual codebase (this branch plus
the unmerged service branches) against standard fintech-platform expectations. Logged here rather
than acted on immediately — see the conversation/ADR 0018 for what *was* acted on (the funding
saga's disbursement side). Items already covered by an existing ADR's deliberate scope-narrowing
(e.g. on-demand-only reconciliation, ADR 0012) are not repeated here; this list is the gaps that
are **not** yet written down anywhere else.

## Critical

- **MFA is not enforced before money moves.** PRD E1-S3 requires it as a hard precondition before
  withdrawal, bank-account change, or first investment. `TotpService`/`SmsMfaService` exist only as
  a login-time feature in authentication-service — nothing in wallet-service, payment-service, or
  investment-service checks MFA status before these actions.
- **No fraud/AML tooling at all.** Zero code for sanctions/PEP screening, transaction monitoring,
  SAR generation, device fingerprinting, or velocity limiting (sign-ups, loan requests,
  withdrawals) — PRD section 8 requires all of these for a CBN-regulated lender.

## Major

- JWT resource servers validate signature + expiry only (via `jwk-set-uri`) — no issuer or
  audience claim validation anywhere.
- No mTLS between services, no KMS/Vault; `FieldCipher`'s encryption key is a bare env var per
  service with no rotation mechanism. PRD section 8 requires both.
- No credit bureau *reporting* back to CRC/FirstCentral (PRD only has lenders reading bureau data
  today). No GL/accounting export, no regulatory periodic reports, no VAT handling — all already
  named in the unmerged `feature/transaction-pin` branch's own PRD v2.0 gap analysis, which should
  be merged soon purely so this list is visible from `main`.
- **Architectural divergence, not a scope cut**: PRD section 6 specifies the funding saga as
  orchestrated by loan-service with one saga id and named compensations (Temporal as a fallback).
  What's built (ADR 0018) is event choreography with the aggregator in investment-service instead.
  Likely the better design, but it diverges from the PRD's written architecture rather than
  narrowing it — worth a PRD update or a dedicated ADR note calling this out explicitly as a
  superseding decision.
- No dead-letter topic and no custom Kafka consumer error handler anywhere — a poison message just
  hits Spring Kafka's default handler. PRD section 6 requires backoff retry then DLQ + alert +
  replay tool.
- OpenTelemetry is a declared dependency in `platform/starter` with zero actual spans/tracer usage
  anywhere — not functional despite being listed as a starter feature.
- Correlation id stops at the HTTP filter layer (`CorrelationIdFilter`) — never threaded into Kafka
  headers (`CloudEventHeaders` carries only CloudEvents attributes) or forwarded on Feign calls
  (only `Authorization` is forwarded today). Breaks the PRD's "gateway through Kafka to the ledger
  posting" traceability requirement at both hops.
- Testcontainers integration tests have never been confirmed passing in any session on record
  (Docker Hub pull speed) — a standing gap across every service built so far, not specific to this
  branch.

## Minor

- CI (`.github/workflows/ci.yml`) is a single `mvn verify` step — no Gitleaks, SBOM, Cosign, Trivy,
  SonarQube, Checkstyle, or ArchUnit, all specified in PRD section 9.
- The PIN-verification work on `feature/transaction-pin` self-acknowledges that other services'
  calls against its endpoint aren't wired up yet either — related to the MFA-enforcement gap
  above.

## Not built anywhere (any branch), named explicitly in the PRD

E12 device binding, E14 collections workflow, E15 restructuring, E16 withholding tax.

## What's already fine — not backlog, just confirmed during the audit

BIGINT kobo used consistently (no stray double/float on money fields); idempotency enforced
(400, not silently accepted) on every money-moving POST checked; BVN/NIN encrypted at rest with a
separate HMAC hash for dedup-safe lookups, matching the PRD exactly; Resilience4j circuit
breakers/retries present on every cross-service gateway; `processed_event`-based consumer
idempotency applied consistently, including every funding-saga consumer built this session.
