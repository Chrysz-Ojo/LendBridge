# ADR 0010: Payment provider adapter interface, scoped to top-up charge-initiation routing

Date: 2026-10-28

## Status

Accepted

## Context

The PRD names "Payment or KYC provider outage" as a risk, with adapter pattern + secondary
provider + circuit breakers as its own mitigation. The user asked for real adapters against all
three Nigerian PSPs available this session — Paystack, Flutterwave, and Monnify — not a fake-only
build, while keeping a fake for local development and CI (CLAUDE.md's external-provider rule).
Direct review of each provider's current API docs confirmed they differ in ways an interface
can't paper over: Paystack's webhook signature is `x-paystack-signature` = HMAC-SHA512 of the raw
body; Flutterwave's `verif-hash` is a static shared-secret string compare, not HMAC, with no real
body-tamper protection; Monnify's `monnify-signature` is HMAC-SHA512 but may be absent entirely in
sandbox. Flutterwave also requires a BVN/NIN to provision a dedicated virtual account; the other
two don't. Monnify authenticates via a cached OAuth-style bearer token (login endpoint, Basic auth,
1-hour TTL) rather than a static API key header.

## Decision

One port, `PaymentProvider`, with per-adapter implementations under
`provider/impl/{fake,paystack,flutterwave,monnify}/`. Webhook signature verification and parsing
(`verifySignature`, `parseWebhookEvent`) are interface methods implemented per-adapter — not
something a shared controller tries to generalize — because the three real mechanisms are
genuinely different, confirmed above. `FakePaymentProvider` is deterministic (an EICAR-style
sentinel reference always fails) and is what every test and CI run exercises; the three real
adapters are production code for whoever has real sandbox credentials, never invoked by anything
automated in this repo.

`PaymentProviderRouter` resolves `payment.provider.primary`/`secondary` from config and routes
**only top-up charge-initiation** through a circuit breaker + retry + secondary-provider fallback.
Virtual-account provisioning and payouts are **not routed**: a dedicated virtual account is
permanently tied to whichever provider created it (no transparent mid-life swap), and a payout
always targets the primary directly, failing loud (`DependencyUnavailableException`) on
circuit-open rather than silently moving money leaving the platform through a different,
un-reviewed provider. Webhooks are never routed — a webhook always belongs to whichever provider's
path it arrived on. Because a charge can be serviced by either the primary or the fallback
secondary, the router returns `RoutedChargeResult` pairing the result with the provider that
actually handled it, so the caller persists the correct provider for later webhook matching
(`provider + providerReference`).

Monnify's payout path is honestly documented as a human-in-the-loop limitation, not faked: a real
payout disbursement requires a merchant-email OTP step that cannot be automated inside a
synchronous `initiatePayout()` call, so `MonnifyPaymentProvider.initiatePayout` always returns
`PENDING` and the OTP-validation endpoint is exposed but never called automatically.

## Consequences

- A fourth real PSP (or a replacement for one of the three) adds one adapter package and a config
  entry — no change to the port, the router, or any service-layer code.
- Reconciliation (ADR 0012) needed a second, narrower interface (`SettlementReportSource`)
  precisely because `PaymentProvider` was deliberately kept to what every real provider actually
  offers synchronously — settlement data isn't one of those things.
- `RoutedChargeResult` is slightly more ceremony than returning `ProviderChargeResult` directly,
  but avoids silently misattributing a fallback-serviced charge to the configured primary, which
  would make that charge's eventual webhook unmatchable.
