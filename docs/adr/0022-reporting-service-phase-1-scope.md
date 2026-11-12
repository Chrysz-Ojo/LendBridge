# ADR 0022: reporting-service Phase 1 scope — real reports only, typed fact tables, Postgres

Date: 2026-11-12

## Status

Accepted

## Context

Per PRD E11-S5, management wants reports covering "loan book, disbursements, repayments, defaults
by grade, user growth, revenue" plus CSV export. Before designing this service, every one of those
six named reports was checked against what data actually exists in the system today:

- **User growth**: real data — `user.registered`.
- **Loan book**: partial — loan-service's Phase 1 (ADR 0015) only implements
  `Draft -> Under review -> Listed/Rejected/Cancelled`. The `LoanStatus` enum has no `FUNDED`,
  `ACTIVE`, `REPAID`, or `DEFAULTED` value; no loan has ever progressed past `Listed` anywhere in
  the codebase (confirmed independently by ADR 0017: "the loan itself never progresses past
  Listed... even once 100% committed"). So this can only be a pipeline/funnel view today, not an
  outstanding-portfolio "book".
- **Disbursements**: no data — no `loan.disbursed` topic or concept exists anywhere.
- **Repayments**: no data — no `repayment.*`/`loan.repaid` topic exists; `payment.succeeded` is
  wallet top-ups/payouts (per its own Avro doc comment), unrelated to loan repayment.
- **Defaults by grade**: no data — no `DEFAULTED` status exists, so nothing ever defaults.
- **Revenue**: no data — `ledger.posted` only carries postings against a single hardcoded
  `PAYMENT_CLEARING_NGN` account; no fee/revenue account exists, and no loan-related postings flow
  into the ledger at all yet.

This isn't a scope choice reporting-service can work around: disbursements, repayments, defaults,
and revenue are all blocked on the funding saga (wallet holds -> escrow -> disbursement -> repayment
schedule) that loan/investment/wallet/ledger haven't built yet. That's a separate, much larger,
multi-service piece of work. Confirmed with the user before finalizing scope, rather than silently
dropping these or fabricating placeholder data.

## Decision

**Phase 1 ships five reports backed by real events**: user growth (`user.registered`), a loan
pipeline funnel (`loan.submitted`/`loan.listed`/`loan.delisted`), risk-grade distribution
(`risk.graded` — the closest real stand-in for "defaults by grade" until defaults exist),
investment/commitment activity (`investment.committed`/`investment.cancelled`), and a KYC
approval-rate report (`kyc.verified`/`kyc.rejected` — not named in E11-S5, but squarely "management
reporting" and fully data-backed, a low-cost addition). Disbursements/repayments/defaults/revenue
are explicitly out of scope, named here as blocked-on-upstream rather than built or faked.

**Storage is plain PostgreSQL**, not ClickHouse. The PRD explicitly allows "PostgreSQL read replica
at launch" as an alternative to its primary ClickHouse suggestion. Confirmed with the user: Postgres
keeps this service on the same Liquibase/JPA/Testcontainers conventions as every other service in
the repo, rather than introducing a first-of-its-kind datastore for a Phase 1 that doesn't need
ClickHouse's scale.

**Typed per-topic fact tables, not a generic sink — a deliberate contrast with audit-service.**
audit-service (built immediately before this) consumes generically because its job is to record
that *something* happened, regardless of shape. reporting-service's job is the opposite: it needs
structured, queryable fields (loan status, risk grade, KYC decision) to run real `GROUP BY`
aggregation. So this service reverts to the standard pattern every pre-audit-service consumer used:
copy the specific `.avsc` schemas it needs (nine, from authentication/loan/risk/investment/kyc
-service), `specific.avro.reader: true`, one typed `@KafkaListener` per topic (three, for
`LoanLifecycleEventConsumer`, since all three loan topics write the same `LoanPipelineFact` shape).
Each fact table is one row per event, not a running counter — aggregation happens at query time via
JPQL `GROUP BY`, avoiding any concurrent-counter-update correctness concern and keeping every
consumer a uniform, trivial insert.

**CSV export is a shared, hand-rolled renderer** (`CsvRenderer`, RFC 4180 quoting), not a new
dependency — every report's query method returns the same `ReportTable {headers, rows}` shape, so
CSV-writing logic exists exactly once (`?format=csv` on each endpoint) instead of once per report.

**Read access is `SUPER_ADMIN`-only**, same reasoning as audit-service's query API: these reports
cross every domain (loans, KYC, investments), so they sit above any single-domain staff role
(`RISK_OFFICER`, `SUPPORT`, etc.) rather than following the "reuse whichever role already fits"
pattern used for single-domain endpoints elsewhere.

## Consequences

- Four of the PRD's six named reports (disbursements, repayments, defaults by grade, revenue) don't
  exist yet. Unblocking them requires the funding saga across loan-service (Funded/Active/
  Repaid/Defaulted states), investment-service, wallet-service (escrow), and ledger-service (a real
  fee/revenue account) — tracked here as the named prerequisite for whoever picks up that work, not
  as a reporting-service defect.
- "Loan book" today means a pipeline/funnel (how many loans reached each stage, and the cumulative
  amount at each stage), not an outstanding-portfolio view — there is no portfolio to report on yet.
- "Risk-grade distribution" is a readable proxy for "defaults by grade" but is not the same metric;
  a reader should not conflate "loans graded D/E" with "loans that defaulted" until real default
  data exists.
- This is the second service this session with no outbox (publishes nothing, same as
  audit-service/marketplace-service) — `platform.outbox.*`/`ce-source` is absent from
  `application.yml`.
- Fact tables grow unboundedly with no retention policy, same open item as audit-service's
  `audit_record` table — worth addressing together in a future compaction/archival pass.
