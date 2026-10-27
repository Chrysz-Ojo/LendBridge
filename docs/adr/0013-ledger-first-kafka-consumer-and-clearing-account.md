# ADR 0013: Ledger-service's first Kafka consumer, and a migration-seeded clearing account

Date: 2026-10-28

## Status

Accepted

## Context

Payment-service never calls ledger-service directly (see payment-service's own design): a
payment's success is usually reported by a webhook, which carries no end-user JWT to forward, the
same wall wallet-service hit when provisioning ledger accounts (ADR 0008). So `payment.succeeded`/
`payment.failed` are events; something must consume them and actually move money, since "only the
Ledger Service can move money." Ledger-service had zero Kafka consumers before this.

Posting the journal entry also needs a second account on the other side of each entry — a
top-up credits the user's wallet account and must debit *something*; a payout is the reverse.

## Decision

1. **Ledger-service consumes `payment.succeeded`/`payment.failed` directly**, in-process: the new
   `PaymentSucceededEventConsumer`/`PaymentFailedEventConsumer` call `JournalEntryService` as a
   plain Spring bean, not over HTTP. This is why wallet-service's lazy-provisioning pattern (ADR
   0008) doesn't apply here — that pattern exists specifically to work around having no caller JWT
   for an *HTTP* call; an in-process method call has no such problem and no provisioning race to
   guard against.
2. **One global clearing account, seeded via a Liquibase migration** (`006-seed-payment-clearing-account.yaml`),
   not created lazily at runtime — `account_type='PAYMENT_CLEARING'`, `normal_balance='DEBIT'`, a
   fixed well-known UUID mirrored as `ClearingAccounts.PAYMENT_CLEARING_NGN` in code. A top-up
   debits this account and credits the wallet; a payout is the reverse.
3. **`payment.succeeded`'s own `idempotencyKey` is reused verbatim** as the posted journal entry's
   idempotency key — a redelivered event can never double-post even if `processed_event`'s own
   dedup check were somehow bypassed.
4. Both `TOP_UP` and `PAYOUT` purposes are handled by the same consumer in v1 (not top-up alone):
   a payout that reduces a wallet's available balance without ever posting a ledger debit would
   leave the *ledger* balance unchanged, directly violating the reason Ledger exists. Any other
   `purpose` value (reserved for a future `REPAYMENT`, once Loan-service exists) is logged and
   ignored, not treated as an error.

## Consequences

- One account, not per-provider: per-provider ledger-level visibility (separate clearing accounts
  for Paystack vs. Flutterwave vs. Monnify) is a cheap additive change later if ever actually
  needed; reconciliation is already scoped per-provider at the Payment-service layer, so this
  isn't a gap today.
- This is the project's first precedent for "a consumer acting as a local caller of another
  module's service layer" rather than "a consumer calling another service over HTTP" — useful to
  recognize next time a Kafka consumer needs to trigger a state change inside the *same* service
  that owns the authoritative write path.
- `payment.failed` never posts anything — there's nothing to reverse — and exists purely so
  ledger-service has its own record of having seen the event, for future audit/reconciliation use.
