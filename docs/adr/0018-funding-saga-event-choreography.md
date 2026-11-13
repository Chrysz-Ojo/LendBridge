# ADR 0018: Funding saga — event choreography across loan/investment/wallet/ledger-service

Date: 2026-11-13

## Status

Accepted

## Context

Every report built on top of loan-service (reporting-service's reports, admin-bff's
reconciliation integration, investment-service's own portfolio ambitions) hit the same wall: a
loan never progresses past `Listed` (ADR 0015/0017's deliberately deferred scope). This closes that
gap: `Listed -> Funding -> Funded -> Disbursing -> Active`, with real money moving — lender holds
becoming real ledger debits, a borrower's wallet actually getting credited, a persisted repayment
schedule.

Three scope decisions, carried through unchanged from the original design:

- **No new auth infrastructure — event choreography throughout.** Converting a lender's hold into
  a debit happens because a loan crossed 100% funded, not because a user clicked something — no
  caller JWT is available for a synchronous cross-service call. Every cross-service trigger is a
  Kafka event consumed by the service that owns the data and the action, exactly ADR 0013's
  existing pattern (ledger-service consuming `payment.succeeded`), applied repeatedly.
- **Automated compensation, not just manual-review fallback.** The only compensable mid-saga state
  is "a lender's hold was captured but disbursement never completed" — the one compensating action
  this design needs is restoring those holds.
- **Wallet-funded repayments only**, deferred to separate scope (see Consequences).

## Decision: the aggregator lives in investment-service, not loan-service

The original plan put the join (waiting for both the hold-capture and borrower-account legs before
posting to the ledger) in loan-service, as a new `DisbursementAssembly` table there. **Built
differently**: the join (`DisbursementAssembler`, `disbursement_assembly` table) lives in
**investment-service**. Reasoning, confirmed by what ledger-service's consumers already expect:

- investment-service already needs to build the lender-leg list (`{lenderLedgerAccountId,
  amountKobo}` per commitment) from its own `Commitment` table — that data never needs to leave
  the service. Putting the join elsewhere would mean shipping that leg list *to* the joiner and
  then immediately needing it back, for no benefit.
- loan-service's role shrinks to what it actually owns: the loan status machine, the
  sign-agreement endpoint, and reacting to the saga's outcome (persist the schedule, go `Active`;
  or revert to `Funded` on failure). It does not need to know about holds or ledger accounts at
  all.

Topic ownership follows: `investment.holds.capture-requested`, `investment.disbursement.ready`,
and `investment.disbursement.compensate` are all investment-service's own outbound topics (not
`loan.*` as first drafted), since it's the service deciding when each fires.

## The choreography, as built

1. **Funding tracking (loan-service)**, consuming `investment.committed`/`investment.cancelled`
   (new consumers — loan-service's first ones, hence its first `processed_event` table): updates
   `fundedAmountKobo` on the loan's own row. First commitment while `Listed` -> `Funding`.
   `fundedAmountKobo >= amountKobo` while `Funding` -> `Funded`. The inverse on cancellation can
   revert `Funded -> Funding`.
2. **Borrower signs** (`POST /api/v1/loans/{id}/agreement/sign`, loan-service, the borrower's own
   JWT). Validates `status == Funded`, computes the origination fee (configurable bps,
   `loan.origination-fee-bps`), transitions to `Disbursing`, publishes `loan.agreement.signed`.
3. **Two legs gather in parallel**, both triggered by `loan.agreement.signed`:
   - *investment-service*: loads every `ACTIVE` commitment, publishes
     `investment.holds.capture-requested`, and seeds its own `DisbursementAssembly` row with the
     origination fee.
   - *wallet-service*: provisions the borrower's ledger account (same lazy-provisioning logic as
     first balance read), publishes `wallet.borrower-account.assembled`.
4. **Hold capture** (wallet-service, triggered by `investment.holds.capture-requested`): flips
   `Hold.status` `ACTIVE -> CONVERTED`. Publishes `wallet.holds.capture-assembled`.
5. **The join** (investment-service's `DisbursementAssembler`): consumes both
   `wallet.holds.capture-assembled` and `wallet.borrower-account.assembled`, upserting into
   `disbursement_assembly` keyed by `loanId` — whichever leg arrives last (the two triggers are
   independent, so either can win the race) finds the other already present and acts:
   - Both succeeded -> builds lender legs fresh from `Commitment` (now carrying
     `lenderLedgerAccountId`, captured free from `createHold`'s response at commitment-creation
     time), publishes `investment.disbursement.ready`, deletes the row.
   - Either failed -> publishes `investment.disbursement.compensate{loanId, holdIds}`, deletes the
     row.
6. **Posting** (ledger-service, triggered by `investment.disbursement.ready`): two journal entries
   in one local transaction via the already-generic `postJournalEntry` (lenders -> `LOAN_ESCROW_NGN`
   -> borrower + `PLATFORM_FEE_REVENUE_NGN`, both migration-seeded global accounts). Publishes
   `ledger.loan-disbursement.completed` on success; on failure, publishes
   `ledger.loan-disbursement.failed` and the transaction itself rolls back for free (both entries or
   neither).
7. **Compensation has two trigger points, one event.** `investment.disbursement.compensate` is
   consumed by **both** wallet-service (restores the named holds `CONVERTED -> ACTIVE`, idempotent)
   and loan-service (reverts `Disbursing -> Funded`). investment-service publishes it from two
   places: the Stage 5 join on failure, and a dedicated consumer of
   `ledger.loan-disbursement.failed` (closing the one gap ledger-service's own build left open —
   its payload's doc comment already named investment-service as the intended consumer, it just
   hadn't been built yet).
8. **Finishing the disbursement** (loan-service, triggered by `ledger.loan-disbursement.completed`):
   generates and **persists** (new `RepaymentSchedule`/`Instalment` tables — previously only ever a
   request-time preview) the instalment schedule, transitions `Disbursing -> Active`, publishes
   `loan.disbursed`.
9. **Holdings** (investment-service, triggered by `loan.disbursed`): creates one `Holding` row per
   commitment (PRD E3-S5, previously nonexistent anywhere). No portfolio-performance screen yet —
   just the data existing.

## Repayment, built in the same session as a follow-up

Same choreography shape, reusing `ledger-service`'s already-built (previously unused)
`LoanRepaymentPoster`/`InvestmentRepaymentReadyEventConsumer`:

1. `POST /loans/{id}/repayments` (borrower JWT, `method` fixed to `WALLET`, loan-service): finds
   the next `Pending` instalment, flips it to `Requested` (the status-guard that makes a retry fail
   fast rather than double-publish — same idiom as `LoanStatus.Disbursing` guarding
   `/agreement/sign`), publishes `loan.repayment.requested`.
2. Two legs gather in parallel: investment-service computes pro-rata lender shares (by original
   commitment size, `HALF_EVEN` rounding, the most-recently-committed lender absorbs the
   remainder) and wallet-service confirms the borrower's ledger account (a plain lookup — it was
   already provisioned at signing, nothing to provision here).
3. **The join is keyed by `instalmentId`, not `loanId`** (`RepaymentAssembly`, a second concrete
   table next to `DisbursementAssembly` — same "two known uses, don't generalize" reasoning). Both
   succeeded -> `investment.repayment.ready` (already consumed, posts the one journal entry: 
   borrower debit, lenders credit pro rata, no escrow hop needed for a single-party-to-many
   posting). Either failed -> `investment.repayment.failed` (no holds exist in this saga, so
   there's nothing else to compensate — only the instalment status needs reverting).
4. `ledger.loan-repayment.completed` (already built, consolidated success/failure on one topic) is
   consumed by loan-service: success flips the instalment `Paid`, and if every instalment on the
   loan is now `Paid`, `Active -> Repaid` + `loan.repaid`. Failure (from either this event or
   `investment.repayment.failed`) reverts the instalment `Requested -> Pending`.
5. **`GET /loans/{id}/schedule` (and the embedded schedule on every `LoanResponse`) now serves the
   real persisted schedule once one exists**, not a recomputed preview — a necessary fix once
   instalments actually carry real `Paid`/`Requested` status, otherwise a borrower who just paid an
   instalment would see a preview claiming it's still unpaid.

## Consequences

- A lender's commitment is never automatically released if a loan is cancelled while `Funding` —
  ADR 0017 already named this gap; it's still open (loan-service's `cancel` only allows
  `Draft`/`Under review`/`Listed`, so a `Funding`/`Funded` loan can't be cancelled from the
  borrower side at all yet, sidestepping rather than closing it).
- `investment.disbursement.compensate` having two independent producers-of-intent (two different
  failure origins) feeding one consumer-side meaning ("revert/restore") on three services is a
  convention worth naming for whoever touches this next: the event means "this loan's disbursement
  did not complete," not "the hold-capture join specifically failed."
