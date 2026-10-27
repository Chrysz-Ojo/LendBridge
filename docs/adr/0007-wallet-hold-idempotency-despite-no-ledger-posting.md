# ADR 0007: Idempotency-Key required on hold creation despite no ledger posting

Date: 2026-10-27

## Status

Accepted

## Context

CLAUDE.md requires an `Idempotency-Key` header on "every money-moving POST." A wallet `Hold`
(`POST /wallets/me/holds`) never posts anything to the ledger — it's a purely wallet-local
reservation against a wallet's available balance, converted into a real ledger debit only later,
by Loan's funding saga (not yet built). Read literally, the money-moving-POST rule might seem not
to apply here, since no money actually moves.

## Decision

`POST /wallets/me/holds` requires `Idempotency-Key` anyway, with the exact mechanism
ledger-service's journal-entry posting already established (ADR 0004): a unique
`hold.idempotency_key` column, look-up-before-insert inside one `@Transactional` method, a
`DataIntegrityViolationException` on save resolved by re-querying the key rather than
propagating an error.

Reasoning: a hold reserves real user funds — it reduces what the user's *available* balance
reports, and (eventually) what they can still commit elsewhere. A client retry without dedup
would double-reserve that balance. The user-visible impact of a double hold (money the user can
no longer use, with no corresponding thing they got for it) is indistinguishable from the impact
of a double ledger debit, even though the mechanism is completely different (an extra DB row
here, not an extra journal entry). The money-moving-POST rule is really about protecting users
from an un-deduplicated retry having a real financial effect — and that's exactly what creating a
second hold does.

## Consequences

- `DELETE /wallets/me/holds/{id}` does **not** need the header — releasing an already-released
  hold is naturally a no-op by the hold's own state, not something a key needs to protect against.
- Any future wallet-service endpoint that reserves, locks, or otherwise constrains a user's funds
  — even if it never itself touches the ledger — should be evaluated against this same question
  ("does an un-deduplicated retry have a real financial effect on the user?") rather than the
  narrower "does this call Ledger?"
