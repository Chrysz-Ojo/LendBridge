# ADR 0023: admin-bff Phase 1 scope — full 11-integration aggregation, generic maker-checker wired to four actions

Date: 2026-11-12

## Status

Accepted

## Context

admin-bff is the last unbuilt component in the PRD's 16-service + 2-edge-component catalogue. Per
the PRD, it "aggregates data for the back-office; enforces staff roles and maker-checker" — the
single thing a staff back-office frontend talks to, calling into the real domain services
synchronously on the staff member's behalf. Architecturally this is a sharp departure from every
service built this session so far: it's a synchronous aggregator/BFF, not an event-driven domain
service — it owns almost no data of its own.

Before designing this, every existing `@PreAuthorize`-gated staff endpoint across the repo was
inventoried: 11 of them, spread across 8 already-built services (kyc-service, loan-service,
risk-service, support-service ×2, messaging-service, notification-service, authentication-service,
audit-service, reporting-service, payment-service). No ADR or prior design existed for admin-bff.

Two scope questions needed answering before any code:

**Reactive or MVC?** PRD section 7's "Web" row says *"Spring Web MVC; Spring WebFlux only for the
gateway and chat"* — api-gateway is reactive (`@EnableWebFluxSecurity`, confirmed), but admin-bff is
not on that exception list. Settled by the PRD itself, no ambiguity: plain Spring MVC.

**Maker-checker, with nothing real to gate.** PRD E11-S3 ties maker-checker to refunds/reversals
specifically ("one admin raises, a different admin approves") — but grepping payment-service,
wallet-service, and ledger-service for `refund|reversal|reverse` turned up zero real capability.
Confirmed with the user before finalizing scope: rather than defer the entire concept, build a
genuinely reusable two-person-approval mechanism now, wired to the existing actions sensitive
enough to deserve it today. Starting from `SUPER_ADMIN` role assignment
(authentication-service's existing `POST /api/v1/auth/admin/users/{id}/roles`), the user asked for
this extended further — reviewed against the full list of staff actions across every service built
this session, three more earned it on the same "a human decision with outsized, hard-to-reverse
consequences" standard, while the rest (ticket status, FAQ CRUD, abuse-report resolution, KYC
decisions, plain loan approve/reject) stayed single-reviewer, matching how the PRD itself describes
them (no second-approver language anywhere for those). The four wired today:

| Action type | Required role | Why maker-checker |
| --- | --- | --- |
| `ROLE_ASSIGNMENT` | `SUPER_ADMIN` | Can cascade into every other privilege in the system |
| `LOAN_REVIEW_DECISION` | `RISK_OFFICER` | A human overriding a computed risk grade |
| `CHANNEL_POLICY_UPDATE` | `SUPER_ADMIN` | Global config change, affects every user's notifications |
| `RECONCILIATION_RUN` | `FINANCE` | Touches real settlement data |

Refunds/reversals stay explicitly blocked until payment-service grows that capability — same
"document the real gap, don't fake it" pattern as ADR 0022's blocked reports.

## Decision

**Full aggregation of all 11 existing staff endpoints**, each as a thin pass-through: a
`@FeignClient` + a `@CircuitBreaker`/`@Retry`-wrapped `*Gateway` service, one package per
integration target, named after the backend it talks to — the exact shape kyc-service's own
`document/{DocumentClient,DocumentGateway}` already established (not a "client layer" / "gateway
layer" split). Local DTOs throughout, no cross-module dependency on any backend service — same
"local copy, no module dependency" idiom as risk-service's own copy of `TrustTier`. Backend-defined
enum fields (ticket status, loan purpose, reconciliation provider, etc.) are kept as plain `String`
in these local DTOs rather than copied enum-for-enum: admin-bff doesn't validate business values
itself, that's each backend's own job, and copying ten small enums across eleven integrations for a
pure proxy layer would be ceremony with no behavioral payoff. The one exception is `Role`
(`SUPER_ADMIN`/`KYC_OFFICER`/`RISK_OFFICER`/`FINANCE`/`SUPPORT`) — a true local enum, because
`ApprovalServiceImpl` constructs the actual role-assignment request body itself from a stored
approval payload, not just passing a caller-supplied string through.

**JWT forwarding, not a service credential.** Every Feign call forwards the original staff member's
`Authorization` header via a `RequestInterceptor` reading `RequestContextHolder` — same mechanism
and same non-`@Configuration`, per-`@FeignClient`-scoped wiring as kyc-service's
`DocumentFeignConfig` (referenced explicitly via `configuration = FeignAuthForwardingConfig.class`
on each client, never registered globally, so a future external-vendor Feign client couldn't
accidentally inherit it and leak a bearer token to a third party). The backend's own
`@PreAuthorize` independently re-enforces the real role check — true defense in depth, zero new
trust granted — and admin-bff's own controller mirrors the same role requirement as a fail-fast
layer in front of that.

**Generic maker-checker (`ApprovalRequest`), wired to four actions.** `raise` → a *different* admin
`decide`s → approved actions execute via dispatch-by-`actionType` (four cases today, each calling
its own gateway with the *approving* admin's own forwarded JWT — `RoleAssignmentGateway`,
`LoanReviewGateway`, `ChannelPolicyGateway`, `ReconciliationGateway`). The core invariant — decider
cannot equal raiser — is enforced with platform-starter's existing `ConflictException` (409), not a
new exception type.

Each action type also carries a `requiredRole()` (the table above) that both `raise` and `decide`
must independently satisfy — checked with `UnauthorizedException` (401, the same type
messaging-service's/support-service's own "not a participant" checks already use for
authorization-not-authentication failures, despite the technically-403-shaped semantics; this repo
already treats it as the generic "you're not allowed to do this" exception). This check lives in
`ApprovalServiceImpl`, not `ApprovalController`: the controller is gated to "any staff role"
(`hasAnyRole('SUPER_ADMIN','RISK_OFFICER','FINANCE','SUPPORT','KYC_OFFICER')`) since it has no way
to know in advance which action type a given request concerns — a `FINANCE` officer needs to reach
the same `POST /api/v1/admin/approvals` endpoint a `SUPER_ADMIN` does. The specific role
requirement is only resolvable once the action type is known, so it's enforced one layer in.

Both outcomes publish an `admin.action.*` event, deliberately metadata-only
(approvalId/actionType/raisedBy/decidedBy/status, no payload echo) — same privacy precedent as
messaging-service's `message.sent`.

**A deliberate side effect**: these `admin.action.approved`/`admin.action.rejected` events match
audit-service's existing generic topic-pattern consumer (`^[a-z]+(_[a-z]+)*\.[a-z_.]+$`, built in
ADR 0021) with zero changes to that service. This partially closes the "no admin-action event
exists anywhere" gap ADR 0021 explicitly flagged as deferred — the first real admin-action signal
in the system now gets captured automatically.

**The `reporting` integration is a raw byte passthrough, not an `ApiResponse`-unwrap-and-rewrap.**
reporting-service's own endpoints already apply the `ApiResponse` envelope for `format=json` and
stream raw `text/csv`/`application/pdf`/`.xlsx` bytes otherwise — admin-bff's `ReportingClient`
returns `ResponseEntity<byte[]>` and forwards it verbatim (status, headers, body), the one
deliberate exception to every other integration's typed-DTO-unwrapped-to-`.getData()` shape.

**Out of scope, explicitly**: "reachable only over VPN" is a network/deployment concern, not
application code. "Staff SSO" is the same authentication-service JWT issuer every other service
already validates against — no new identity provider.

## Consequences

- Refunds/reversals remain unimplemented — unblocking them needs a real endpoint in
  payment-service first, tracked here as the named prerequisite, not an admin-bff defect.
- Four action types are wired through maker-checker today. Extending it to another sensitive
  action (e.g. a future refund approval) means adding an `ApprovalActionType` value with its
  `requiredRole()`, an action-payload DTO, and a matching dispatch branch in
  `ApprovalServiceImpl.execute(...)` — the mechanism itself needs no changes.
- Three of the four (`LOAN_REVIEW_DECISION`, `CHANNEL_POLICY_UPDATE`, `RECONCILIATION_RUN`) used to
  have direct passthrough endpoints (`LoanReviewController`/`ChannelPolicyController`/
  `ReconciliationController`'s write methods); those are now removed, each controller keeping only
  its read-only method(s). A caller who previously hit those endpoints directly must now go
  through `ApprovalController` instead.
- This service introduces `spring-cloud-starter-openfeign` and `resilience4j-spring-boot3` as
  dependencies for the first time this session (both already version-managed via platform-bom's
  Spring Cloud / Resilience4j BOM imports, same as kyc-service's prior usage — no new version
  pinning needed) and WireMock as a new test-scope-only dependency (no production impact).
- Local DTOs mean a backend API change (new field, renamed enum) won't automatically propagate to
  admin-bff — each of the 11 integrations needs its own DTO updated if the corresponding backend's
  contract changes. Accepted cost of the "no cross-module dependency" convention already used
  throughout this session, not unique to this service.
- admin-bff has essentially no retention/compaction concern of its own: `approval_request` only
  grows with actual maker-checker usage (one row per raise), unlike audit-service's/
  reporting-service's unbounded event-driven fact tables.
