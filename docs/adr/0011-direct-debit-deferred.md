# ADR 0011: Direct-debit mandate creation/activation/execution deferred

Date: 2026-10-28

## Status

Accepted

## Context

All three PSPs support a real recurring-debit mandate product, but each requires a meaningfully
more complex, asynchronous customer-authorization step than a one-off card/bank-transfer/wallet
charge, confirmed via direct review of each provider's docs: Paystack requires routing the
customer through a NIBSS consent-transfer flow; Flutterwave requires requesting special merchant
access from their support team plus a ₦100 authorization charge with a 10-minute token window;
Monnify issues a mandate authorization link with a 30-day activation window. None of this is a
same-request synchronous operation the way initiating a charge or a transfer is, and none of it has
real settlement/webhook test coverage available in this environment.

## Decision

Ship the schema so nothing has to change shape later, but no v1 code path creates, activates, or
debits a mandate: `payment_mandate` table (`provider_mandate_reference`, `status` —
`PENDING_AUTHORIZATION|ACTIVE|REVOKED|EXPIRED` — `authorization_url`, `token_expires_at`,
`activated_at`, `revoked_at`) and a `RepaymentMethod` enum (`WALLET|CARD|BANK_TRANSFER|DIRECT_DEBIT`)
reserved on `payment_intent`. Any code path that would select `DIRECT_DEBIT` throws
`UnprocessableEntityException("DIRECT_DEBIT_NOT_YET_SUPPORTED", ...)` rather than attempting a
partial or fabricated implementation.

## Consequences

- Direct debit becomes its own follow-up build once there's a real mandate-authorization callback
  flow to build and test against, rather than a half-finished, untested code path shipped now.
- `payment_intent.repayment_method` being nullable in v1 (only meaningful once Loan-service exists
  to drive repayments at all) is intentional, not an oversight — see the "what's deferred" note on
  repayment orchestration generally.
