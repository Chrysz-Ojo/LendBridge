# ADR 0005: No continuous balance-check background job in ledger-service v1

Date: 2026-10-27

## Status

Accepted

## Context

The PRD describes the Ledger Service's correctness bar as "Zero tolerance for ledger imbalance:
sum of debits equals sum of credits, checked continuously," and names "Append-only; continuous
balance check" in the service catalogue. Read literally, this suggests an ongoing operational
control — a background process that independently re-verifies the whole ledger's invariant over
time, catching drift from any source, not just the API.

## Decision

v1 does not build that background job. Instead, correctness is enforced entirely at write time:
every `POST /journal-entries` call validates, inside one `@Transactional` method, that every
referenced account exists, all postings share one currency, and the sum of debit postings equals
the sum of credit postings — before anything is persisted. Since `journal_entry` and `posting`
are append-only (no update or delete path exists anywhere in the service), an entry that passed
this check at insert time stays balanced forever; there is no code path that could make it
unbalanced afterward.

## Consequences

- The invariant cannot be violated through the API. It could still be violated by something
  outside the API's control — a direct database write, a migration bug, manual data surgery — and
  v1 has no independent process that would ever detect that.
- This is an accepted, named gap, not an oversight: build the continuous reconciliation sweep
  before ledger-service accepts real production traffic, specifically once Wallet, Payment or
  Loan exist and are calling it for real — that is the point at which bypass risk (and the cost
  of not catching it) actually becomes material.
