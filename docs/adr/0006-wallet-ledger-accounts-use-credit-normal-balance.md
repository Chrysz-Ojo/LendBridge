# ADR 0006: Wallet ledger accounts use CREDIT as normal balance

Date: 2026-10-27

## Status

Accepted

## Context

Every wallet's balance is a view over a ledger-service `Account` (ADR 0004/0005's double-entry
engine). Opening that account requires choosing a `normalBalance` (`DEBIT` or `CREDIT`) — Ledger
never infers it; `AccountServiceImpl.getBalance` computes `creditTotal − debitTotal` for a
`CREDIT`-normal account and `debitTotal − creditTotal` for a `DEBIT`-normal one. Getting this
wrong isn't a style choice — it determines whether crediting the account increases or decreases
the reported balance.

## Decision

Wallet ledger accounts are opened with `normalBalance = CREDIT`. A user's wallet is a
customer-fund liability on the platform's own books (the platform owes that money to the user),
and liabilities are credit-normal in standard double-entry accounting: crediting the account
(money coming in) increases the balance; debiting it (money going out) decreases it. This matches
the intuitive "funding my wallet increases my balance" behavior precisely because it's the
correct accounting treatment, not despite it.

## Consequences

- Every future credit-side posting to a wallet account (top-ups, repayments received, loan
  disbursement credits) increases `balanceKobo` as expected. Choosing `DEBIT` here would have
  silently inverted that relationship — a real bug, not just an unconventional modeling choice.
- Anyone opening a new kind of account against Ledger (escrow, fee/revenue, etc.) should apply
  the same reasoning: ask "is this a liability (credit-normal) or an asset (debit-normal) from
  the platform's own books' perspective," not "does crediting it feel like it should increase or
  decrease."
