# ADR 0012: Reconciliation scoped to on-demand; daily scheduled job deferred

Date: 2026-10-28

## Status

Accepted

## Context

Mirrors ledger-service's own ADR 0005 ("no continuous balance-check job in v1") directly: a
scheduled reconciliation job is only worth building once there's a real settlement file or report
to test it against, and none exists in this environment — none of the three PSPs' real settlement
exports have been exercised this session, only their transactional APIs.

## Decision

Build the schema (`reconciliation_run`, plus `provider_transaction.reconciliation_run_id` and
`match_status` columns linking individual provider-transaction rows to a run) and one on-demand
method/endpoint (`POST /api/v1/payments/reconciliation-runs`, gated
`@PreAuthorize("hasRole('FINANCE')")` — reusing the `@EnableMethodSecurity`/`@PreAuthorize`
precedent already established in authentication-service, not a new pattern) that diffs
payment-service's own `provider_transaction` rows for a period against a settlement report.

Settlement data isn't uniformly available from any provider's transactional API — in reality it's
an out-of-band file or dashboard export — so a second, narrower interface,
`SettlementReportSource`, is deliberately **not** part of `PaymentProvider` (ADR 0010) and is
implemented only by `FakePaymentProvider`; a real adapter that doesn't implement it causes
`runReconciliation` to reject with `UnprocessableEntityException("SETTLEMENT_REPORT_UNAVAILABLE",
...)` rather than fabricate a settlement report. Matching in v1 is scoped to provider-reference
presence (not amount-level diffing), since there is no real settlement data to validate a richer
comparison against yet.

No daily scheduled job exists; nothing in this build triggers reconciliation automatically.

## Consequences

- Once a real settlement file format exists to test against, the scheduled job and
  amount-level/currency-level matching are additive — the schema and the on-demand method don't
  need to change shape, only gain a caller and a richer comparison.
- `SettlementReportSource` returning an empty list for the fake (no real settlement file to
  reproduce) means reconciliation against the fake provider is only exercised in tests that
  construct `SettlementRecord` fixtures directly, not through `FakePaymentProvider` producing
  realistic data.
