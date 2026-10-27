# ADR 0008: Lazy ledger-account provisioning, not eager-in-consumer

Date: 2026-10-27

## Status

Accepted

## Context

Every user needs a wallet (PRD E10-S1), and wallet-service auto-provisions one by consuming
`user.registered` (the same Kafka-consumer + `processed_event` dedup pattern user-profile-service
already established for this same topic). A wallet's balance is a view over a ledger-service
`Account`, so provisioning a wallet fully would seem to mean: on consuming `user.registered`,
create the local `Wallet` row *and* call Ledger's `POST /accounts` to open its backing account.

That second part doesn't work. There is no service-to-service (client-credentials/m2m) auth
mechanism anywhere in this repo — ledger-service's `SecurityConfig` is a pure OAuth2 resource
server that validates "is this a validly-signed JWT," with no issuer for machine callers. A Kafka
consumer has no inbound HTTP request, so there is no caller JWT to forward to Ledger. Every other
place wallet-service talks to Ledger happens inside an end-user-authenticated HTTP request, where
the caller's own already-validated JWT can simply be forwarded as-is (`LedgerFeignConfig`'s
`RequestInterceptor`) — this works only because ledger-service checks token validity, not subject.

## Decision

The `user.registered` consumer only inserts the local `Wallet` row (`ledger_account_id = null`)
and publishes `wallet.created` — no network call. The ledger account is provisioned lazily, on
first authenticated access: `WalletServiceImpl.getOrCreateWallet` takes a pessimistic row lock
(`SELECT ... FOR UPDATE`) on the wallet row, and if `ledger_account_id` is still null, calls
Ledger's `POST /accounts` forwarding the live caller's `Authorization` header, then persists the
result. Concurrent first-accesses to the same wallet serialize on the row lock, so exactly one
caller performs the provisioning.

## Consequences

- A wallet can exist (as a DB row) for a user who has never made an authenticated wallet-service
  call — its `ledger_account_id` stays null until they do. This is intentional, not a bug: nothing
  reads a wallet's balance before some authenticated request needs it anyway.
- This solves wallet-service's specific problem, not the general one: there is still no
  service-to-service auth anywhere in the repo. The next service that needs to call another
  service from a context with no live caller JWT (a scheduled job, another Kafka consumer) will
  hit the same wall and needs its own answer — this ADR is not a platform-wide fix, and shouldn't
  be read as one.
