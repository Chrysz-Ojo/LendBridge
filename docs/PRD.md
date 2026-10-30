# LendBridge — Product Requirements Document (Microservices, v2.0)

Oct 21, 2026 · @Noah

## 1. Document control and executive summary

LendBridge v2.0 rebuilds the completed MVP as a production-grade, event-driven microservices platform on Spring Boot, Kafka and Kubernetes, so lending, payments and risk can scale and ship independently.

| Field | Value |
| --- | --- |
| Product | LendBridge: peer-to-peer (P2P) lending platform |
| Version | 2.0 (microservices re-platform of the MVP) |
| Product Manager | Abdullahi Fariu-Arebi |
| Product Designer | Godswill Desmond |
| Platforms | Web (responsive) at launch; mobile apps reuse the same public APIs later |
| Primary market | Nigeria (BVN-based identity, NGN wallets) |
| Related artefacts | Concept notes, design flows, system architecture, engineering Jira board |

### Executive summary

LendBridge connects borrowers directly with individual lenders. Borrowers get faster, cheaper credit than traditional banks offer; lenders earn interest and spread risk across many loans.

The MVP proved the flow end to end: register, verify, apply or invest, match, fund, repay, track. v2.0 keeps every MVP feature and splits the system into 16 domain services behind an API gateway. Each service owns its data and talks to others through REST (synchronous queries) and Kafka events (state changes).

The re-platform targets four outcomes:

1. **Reliability of money movement**: a double-entry ledger, idempotent payments and the saga pattern so no naira is lost or double-counted.
2. **Regulatory readiness**: KYC/BVN checks, audit trails, data residency and privacy controls designed in, not added later.
3. **Independent scaling and delivery**: teams deploy the loan, payment or notification service without a full-platform release.
4. **Trust for lenders**: transparent borrower trust levels, repayment history and risk grades on every listing.

### Vision

To give individuals and small businesses easy, quick and affordable credit, through a transparent, efficient and inclusive lending marketplace.

## 2. Goals, success metrics, scope and assumptions

Success means a borrower is verified and listed in under 10 minutes, funded in under 72 hours, and every naira is traceable in the ledger. The targets below are proposed starting points for the team to confirm.

### Business goals

- Lower borrowing cost: borrower APR consistently below comparable bank and digital-lender offers.
- Grow a healthy two-sided marketplace: enough lender capital to fund most approved requests.
- Keep defaults manageable through verification, trust levels and risk grading.
- Earn revenue through a platform service fee on funded loans (fee model to be confirmed by Finance).

### Success metrics (proposed)

| Metric | Target | Owner |
| --- | --- | --- |
| Sign-up to KYC-verified | < 10 minutes median | Identity squad |
| KYC auto-approval rate | ≥ 80% | Identity squad |
| Loan request to fully funded | < 72 hours median | Marketplace squad |
| Share of approved requests funded | ≥ 70% | Marketplace squad |
| On-time repayment rate | ≥ 90% of instalments | Risk and collections |
| 90-day default rate | < 5% of funded value | Risk and collections |
| Payment reconciliation breaks | 0 unresolved after T+1 | Payments squad |
| Platform availability | 99.9% monthly for core APIs | Platform squad |
| API p95 latency | < 300 ms for reads, < 800 ms for writes | Platform squad |
| Support first response | < 2 hours in business hours | Support |

### In scope for v2.0

- Registration, login, MFA and session management
- Profile creation and KYC: ID card image, BVN, bank statement (≤ 3 months old), address, phone, email
- Role selection: lender, borrower, or both on one account
- Lender investment preferences, loan marketplace, manual and auto-invest
- Borrower loan applications with documents, offers, acceptance
- Loan agreement generation and e-acceptance
- Wallets, funding, disbursement, repayment (early, on-time, late) and repayment schedules
- Risk grading, borrower trust levels and loan performance history
- In-app chat between matched lenders and borrowers; support ticketing
- Email, SMS, in-app and push-ready notifications
- Admin back-office: KYC review, loan moderation, disputes, refunds, reports
- Audit trail, reporting and reconciliation

### Out of scope for v2.0

- Educational resources and learning content (FAQs are in scope)
- Native mobile apps (APIs are ready for them)
- Secondary market for selling loan parts between lenders
- Multi-currency and cross-border lending
- Automated legal recovery and third-party debt collection integrations

### Assumptions

- Loans are single-borrower, and one loan can be funded by one or many lenders (fractional funding).
- Money is held in segregated accounts with a licensed partner bank or payment provider, never in LendBridge's operating account.
- Interest is simple interest on reducing balance, repaid in equal monthly instalments, unless Product changes it.
- Nigeria is the only launch market; amounts are NGN stored in kobo (integer minor units).

### Open questions

- Which licence route will LendBridge operate under (digital lending registration with FCCPC, SEC crowdfunding rules, or a partner licence)? Legal to confirm.
- Who carries default risk: lenders only, or is there a provision fund?
- Fee model: borrower origination fee, lender service fee on interest, or both?
- Payment and KYC vendors: final choice after commercial review (candidates are listed in section 7).

## 3. Personas and end-to-end journeys

Four personas drive the requirements: two marketplace users and two internal operators who keep the marketplace safe.

| Persona | Who they are | What they need most | Main pain today |
| --- | --- | --- | --- |
| Individual borrower | Salaried worker or freelancer needing ₦50k–₦2m for rent, school fees, emergencies | Fast decision, fair rate, clear repayment schedule | Bank paperwork; predatory loan apps |
| SME borrower | Small business owner needing ₦500k–₦10m working capital | Larger amounts, longer tenors, business-plan upload | Collateral demands; slow approvals |
| Individual lender | Professional with idle savings wanting better returns than savings accounts | Clear risk view, diversification, steady repayments | Low deposit rates; no visibility into risk |
| Operations admin | LendBridge staff in KYC, risk, support and finance | Review queues, audit trail, reconciliation, dispute tools | Manual spreadsheets in the MVP |

One account can hold both lender and borrower roles. The role chosen at onboarding sets the default dashboard; the other role can be activated later.

### Borrower journey

1. Sign up with name, email, phone and password; accept terms and privacy policy; verify email and phone by OTP.
2. Complete profile and KYC: BVN, government ID image, selfie for liveness, address, bank statement no older than 3 months, employment and income.
3. Receive a KYC decision and trust level (Tier 1–3); the trust level caps the amount that can be requested.
4. Apply for a loan: amount, tenor, purpose, maximum interest rate, supporting documents.
5. Risk Service grades the request (A–E); compliance checks pass; the request is listed in the marketplace.
6. Receive offers from lenders, or automatic funding from auto-invest lenders; compare and accept.
7. Sign the loan agreement electronically once the loan is 100% funded (or at the accepted partial threshold).
8. Funds are disbursed to the borrower's wallet and can be withdrawn to a verified bank account.
9. Repay from wallet, card, bank transfer or direct debit: early, on time or late; see the schedule and history.
10. Loan closes when fully repaid; trust level and history update.

### Lender journey

1. Sign up and complete KYC (same flow; bank statement may be optional for lender-only Tier 1).
2. Set investment preferences: amount per loan, total budget, minimum interest rate, risk grades accepted, tenor range.
3. Fund the wallet by bank transfer or card.
4. Browse matching listings or switch on auto-invest with the saved preferences.
5. Review borrower profile, trust level, risk grade, documents summary and loan history; chat with the borrower if needed.
6. Commit an amount to a loan (minimum ticket, for example ₦5,000); funds are reserved in the wallet.
7. When the loan is fully funded and signed, reserved funds move to the borrower.
8. Receive repayments (principal plus interest, less platform fee) into the wallet; track performance in the portfolio dashboard.
9. Withdraw earnings or reinvest.

### Admin journey

1. Work the KYC review queue for cases the provider could not auto-approve.
2. Moderate flagged loan requests and suspicious activity.
3. Handle support tickets and disputes; issue refunds through maker-checker approval.
4. Run daily reconciliation between the ledger and the payment provider; resolve breaks.
5. Export regulatory and management reports.

## 4. Functional requirements

The platform delivers 11 epics: the 8 MVP epics for lenders and borrowers, plus 3 platform epics (identity, wallet and payments, admin) that the MVP left implicit. Priority: P0 = launch blocker, P1 = launch target, P2 = fast follow. Each story names the service that owns it (section 5).

### E1. Account and authentication (both roles)

| ID | User story | Acceptance criteria | Priority | Service |
| --- | --- | --- | --- | --- |
| E1-S1 | As a visitor, I want to sign up with name, email, phone and password so I can use LendBridge. | Password ≥ 10 chars with complexity rules; email and phone unique; terms and privacy consent stored with version and timestamp; OTP sent to phone and link to email; account inactive until both verified. | P0 | Authentication |
| E1-S2 | As a user, I want to log in securely. | Login by email or phone; lockout after 5 failed attempts for 15 minutes; JWT access token (15 min) and rotating refresh token (7 days); new-device login triggers an alert. | P0 | Authentication |
| E1-S3 | As a user, I want two-factor authentication to protect my money. | TOTP or SMS OTP; mandatory before any withdrawal, bank-account change or first investment. | P0 | Authentication |
| E1-S4 | As a user, I want to reset my password. | Reset link valid 30 minutes, single use; all sessions revoked after reset. | P0 | Authentication |
| E1-S5 | As a user, I want to choose lender, borrower or both. | Role chosen after sign-up; second role can be added later from settings; each role has its own dashboard. | P0 | User Profile |

### E2. Profile and KYC verification (both roles)

| ID | User story | Acceptance criteria | Priority | Service |
| --- | --- | --- | --- | --- |
| E2-S1 | As a user, I want to create my profile with personal and financial details. | Captures name, DOB, address, phone, email, employment status, employer or business, monthly income; partial saves allowed. | P0 | User Profile |
| E2-S2 | As a user, I want my identity verified with my BVN and NIN. | BVN and NIN validated with the KYC provider; name and DOB must match profile within the configured tolerance; mismatches go to manual review. | P0 | KYC |
| E2-S3 | As a borrower, I want to upload my ID card image, selfie and bank statement. | Accepts JPG, PNG, PDF ≤ 10 MB; bank statement must be dated within the last 3 months; files virus-scanned and stored encrypted; liveness check on selfie. | P0 | KYC, Document |
| E2-S4 | As a user, I want to know my verification status and trust level. | Status: Pending, Verified, Rejected, Needs more info; trust tier 1–3 shown with the borrowing or lending limit it unlocks; rejection reason shown. | P0 | KYC |
| E2-S5 | As a user, I want to add and verify the bank account I withdraw to. | Account name lookup must match KYC name; only verified accounts can receive withdrawals. | P0 | Wallet |

### E3. Lender: investment management

| ID | User story | Acceptance criteria | Priority | Service |
| --- | --- | --- | --- | --- |
| E3-S1 | As a lender, I want to set investment preferences: amount, interest rate, risk tolerance and period. | Lender can save several preference sets (amount per loan, total budget, min interest rate, accepted risk grades A–E, tenor range); editable anytime. | P0 | Investment |
| E3-S2 | As a lender, I want to browse loan listings that match my preferences. | Listing shows amount, amount funded so far, interest rate, tenor, purpose, risk grade, trust level; filter and sort by each; default view = matches for active preferences. | P0 | Marketplace |
| E3-S3 | As a lender, I want to diversify across many loans. | Minimum ticket configurable (default ₦5,000); maximum exposure to one borrower configurable (default 20% of lender's portfolio); portfolio shows spread by grade and tenor. | P0 | Investment |
| E3-S4 | As a lender, I want auto-invest to fund matching loans for me. | Opt-in per preference set; invests only while wallet balance covers the ticket; every auto-investment is logged and notified; pausable anytime. | P1 | Investment |
| E3-S5 | As a lender, I want to monitor my investments and earnings. | For each active loan: total amount, my share, interest rate, amount repaid, duration, number of repayments, late, missed and on-time payments; portfolio totals: invested, outstanding, interest earned, fees, net return. | P0 | Investment |

### E4. Lender: loan selection and funding

| ID | User story | Acceptance criteria | Priority | Service |
| --- | --- | --- | --- | --- |
| E4-S1 | As a lender, I want to select a loan and specify my investment amount. | Amount ≥ minimum ticket and ≤ remaining unfunded amount; funds reserved (held) in wallet immediately; commitment cancellable until the loan is fully funded. | P0 | Investment, Wallet |
| E4-S2 | As a lender, I want to make a direct offer to a borrower on my terms. | Offer carries amount, rate and tenor; expires after 72 hours if not accepted; reserved funds released on expiry or rejection. | P1 | Marketplace |
| E4-S3 | As a lender, I want a secure way to transfer funds to borrowers. | Transfers run through the platform ledger only (no direct bank details shared); a funding saga either disburses all reserved funds or releases all of them. | P0 | Payment, Ledger |
| E4-S4 | As a lender, I want notifications about my loans. | Notified when the borrower receives the loan, a repayment is due, a repayment is made, a repayment is late, the loan matures, the loan is fully repaid. | P0 | Notification |

### E5. Lender: risk assessment and due diligence

| ID | User story | Acceptance criteria | Priority | Service |
| --- | --- | --- | --- | --- |
| E5-S1 | As a lender, I want comprehensive borrower information. | Profile shows verified status, trust level, employment, income band, credit bureau summary (where consented), document checklist (not the raw documents). | P0 | Marketplace, Risk |
| E5-S2 | As a lender, I want risk tools for each application. | Each listing has a risk grade A–E, expected default band and debt-to-income ratio; grading model version recorded. | P0 | Risk |
| E5-S3 | As a lender, I want to rely on platform verification. | Only KYC-verified borrowers can be listed; verification badges show which checks passed (BVN, NIN, ID, liveness, bank statement). | P0 | KYC |
| E5-S4 | As a lender, I want historical performance data. | Borrower profile lists previous and ongoing loans with on-time %, late and missed counts; platform-wide default rates by grade are published. | P1 | Loan, Reporting |

### E6. Borrower: loan application

| ID | User story | Acceptance criteria | Priority | Service |
| --- | --- | --- | --- | --- |
| E6-S1 | As a borrower, I want to apply by specifying amount, tenor, purpose and interest rate. | Amount within trust-tier limit; tenor 1–24 months; purpose from a list plus free text; rate within platform floor and cap; total repayment and instalment shown before submit. | P0 | Loan |
| E6-S2 | As a borrower, I want to upload supporting documents. | Income statements, business plan or invoices; same file rules as KYC uploads. | P0 | Document |
| E6-S3 | As a borrower, I want to see all my loan requests. | List with status (Draft, Under review, Listed, Funding, Funded, Active, Repaid, Defaulted, Cancelled, Expired) and timestamps. | P0 | Loan |
| E6-S4 | As a borrower, I want to cancel a request before it is funded. | Cancel allowed in Draft, Under review, Listed, Funding; all lender reservations released and lenders notified. | P0 | Loan |
| E6-S5 | As a borrower, I want only one active request at a time. | Configurable limit (default 1 open request, 2 active loans); clear error when exceeded. | P1 | Loan |

### E7. Borrower: loan offers and selection

| ID | User story | Acceptance criteria | Priority | Service |
| --- | --- | --- | --- | --- |
| E7-S1 | As a borrower, I want to receive offers from interested lenders. | Commitments and direct offers appear in real time on the request; listing expires after 14 days if not funded (configurable). | P0 | Marketplace |
| E7-S2 | As a borrower, I want to compare offers. | Side-by-side: amount, rate, tenor, monthly instalment, total cost, lender trust level. | P1 | Marketplace |
| E7-S3 | As a borrower, I want to accept an offer and sign the agreement. | Agreement PDF generated from a versioned template; e-acceptance by OTP; signed copy stored and emailed to all parties. | P0 | Loan, Document |
| E7-S4 | As a borrower, I want to accept partial funding. | Borrower may accept when ≥ 80% funded (configurable); schedule recalculated before signing. | P2 | Loan |
| E7-S5 | As a borrower, I want status notifications. | Notified on: listed, new offer or commitment, fully funded, agreement ready, disbursed. | P0 | Notification |

### E8. Borrower: repayment and tracking

| ID | User story | Acceptance criteria | Priority | Service |
| --- | --- | --- | --- | --- |
| E8-S1 | As a borrower, I want my repayment schedule. | Every instalment shows due date, principal, interest, fee and total; outstanding balance shown. | P0 | Loan |
| E8-S2 | As a borrower, I want to repay securely and conveniently. | Pay from wallet, card, bank transfer (virtual account) or direct-debit mandate; early, on-time and late payments accepted; early payoff quote available. | P0 | Payment, Wallet |
| E8-S3 | As a borrower, I want my repayment history. | Each payment tagged Early, On time or Past due, with date and amount; downloadable statement. | P0 | Loan |
| E8-S4 | As a borrower, I want reminders before payments are due. | Reminders 7 days, 3 days and 1 day before due; on due date; after a missed payment. | P0 | Notification |
| E8-S5 | As the platform, we want late payments handled consistently. | Grace period 3 days; then late fee per policy; loan marked Defaulted after 90 days past due; repayments allocated fee → interest → principal and split pro rata to lenders. | P0 | Loan, Ledger |

### E9. Communication and customer support (both roles)

| ID | User story | Acceptance criteria | Priority | Service |
| --- | --- | --- | --- | --- |
| E9-S1 | As a lender or borrower, I want to chat with my counterpart about a loan. | Chat opens only between a borrower and lenders who viewed or committed to their request; real time via WebSocket; contact details, account numbers and links auto-masked; messages retained for audit. | P1 | Messaging |
| E9-S2 | As a user, I want to contact the support team. | In-app ticket with category, attachments and status; support replies in the same thread; email copy of replies. | P0 | Support |
| E9-S3 | As a user, I want platform and loan news. | In-app notification centre with read and unread state; user controls marketing opt-in separately from transactional messages. | P1 | Notification |
| E9-S4 | As a user, I want FAQs. | Searchable FAQ page managed by admins. Educational resources remain out of scope. | P1 | Support |
| E9-S5 | As a user, I want to report abuse in chat. | Report button flags the thread to the admin queue. | P1 | Messaging |

### E10. Wallet and payments (platform)

| ID | User story | Acceptance criteria | Priority | Service |
| --- | --- | --- | --- | --- |
| E10-S1 | As a user, I want a wallet to hold my funds. | Each user has an NGN wallet with available and held balances; balance derived from the ledger, never edited directly. | P0 | Wallet, Ledger |
| E10-S2 | As a user, I want to fund my wallet. | Dedicated virtual account per user and card top-up; funds credited only after provider webhook is verified. | P0 | Payment |
| E10-S3 | As a user, I want to withdraw to my bank. | 2FA required; daily limits by trust tier; status Pending, Successful, Failed, Reversed. | P0 | Payment |
| E10-S4 | As finance, we need every transaction to be idempotent and reconcilable. | Every money request carries an idempotency key; double-entry ledger; daily automated reconciliation with provider settlement report. | P0 | Ledger, Payment |

### E11. Admin back-office (internal)

| ID | User story | Acceptance criteria | Priority | Service |
| --- | --- | --- | --- | --- |
| E11-S1 | As a KYC officer, I want to review flagged verifications. | Queue with documents, provider results and decision buttons; reason required for reject. | P0 | Admin, KYC |
| E11-S2 | As a risk officer, I want to moderate loan requests. | Approve, reject or request info on flagged requests; override grade with a reason logged. | P1 | Admin, Risk |
| E11-S3 | As finance, I want refunds and reversals with approval. | Maker-checker: one admin raises, a different admin approves. | P0 | Admin, Payment |
| E11-S4 | As an admin, I want a full audit trail. | Every admin action and every state change on users, loans and money recorded immutably with actor, time, before and after. | P0 | Audit |
| E11-S5 | As management, I want reports. | Loan book, disbursements, repayments, defaults by grade, user growth, revenue; CSV export. | P1 | Reporting |
| E11-S6 | As an admin, I want role-based access. | Roles: Super admin, KYC officer, Risk officer, Finance, Support; least privilege enforced per endpoint. | P0 | Authentication |

### Loan lifecycle states

| State | Entered when | Next states |
| --- | --- | --- |
| Draft | Borrower starts an application | Under review, Cancelled |
| Under review | Submitted; risk grading and checks run | Listed, Rejected |
| Listed | Approved and visible in the marketplace | Funding, Expired, Cancelled |
| Funding | First lender commitment received | Funded, Expired, Cancelled |
| Funded | 100% (or accepted partial) funded, agreement pending | Active, Cancelled |
| Active | Agreement signed and funds disbursed | Repaid, Defaulted |
| Repaid | Final instalment received | — (terminal) |
| Defaulted | 90 days past due | Repaid (recovered), Written off |
| Rejected, Expired, Cancelled, Written off | As named | — (terminal) |

### Key business rules

- Money is stored as integer kobo; interest is computed with banker's rounding at instalment level and the rounding remainder goes to the final instalment.
- Monthly instalment uses the standard amortisation formula on the agreed annual rate.
- Platform fee is deducted from each lender's interest share at payout; the fee rate is configuration, not code.
- All configurable limits (tickets, tiers, grace periods, expiry windows) live in a config table editable by Super admin with audit logging.

## 5. Microservices architecture and service catalogue

LendBridge v2.0 runs 16 domain services behind one API gateway on Kubernetes; services own their data, query each other over REST or gRPC, and announce every state change on Kafka.

&#91;embedded content: target architecture · 16 domain services in 5 squads\]

Only the Ledger Service can move money; Wallet balances are views over the ledger, and the Loan Service orchestrates the funding saga across Investment, Wallet and Ledger (section 6).

### Architecture principles

- **Bounded contexts**: one service per business capability, owned by one squad.
- **Database per service**: no shared tables; data crosses boundaries only through APIs or events.
- **Events for state, APIs for queries**: commands that change state publish events via the transactional outbox.
- **Money is special**: double-entry, append-only ledger; idempotency on every money call; sagas with compensation instead of distributed transactions.
- **Stateless services**: any pod can serve any request; state lives in databases, Redis or Kafka.
- **Design for failure**: timeouts, retries with backoff, circuit breakers, bulkheads and dead-letter topics on every integration.
- **Zero trust inside the cluster**: mTLS between services and JWT propagation; every service authorises its own endpoints.
- **Pragmatic start**: services with low load (Support, Reporting, Audit) can begin as separate modules in fewer deployables and split out when needed, without changing their contracts.

### Service catalogue

| Service | Responsibility | Publishes | Notes |
| --- | --- | --- | --- |
| API Gateway | Single entry point: routing, TLS, JWT validation, rate limiting, request ids, CORS | — | Spring Cloud Gateway; WebSocket pass-through for chat |
| Admin BFF | Aggregates data for the back-office; enforces staff roles and maker-checker | — | Reachable only over VPN and staff SSO |
| Authentication | Sign-up, login, MFA, tokens, password reset, roles; own JWT issuer | user.registered | Issues and validates its own OAuth2/OIDC-style JWTs; no external identity provider |
| User Profile | Personal, employment and income data; role flags; preferences | profile.updated | PII encrypted at field level |
| KYC | BVN, NIN, ID, liveness, bank-statement checks; trust tier; manual review cases | kyc.verified, kyc.rejected | Provider adapters with fallback |
| Document | Uploads, virus scan, encrypted storage, signed URLs; agreement PDFs | document.uploaded | Pre-signed S3 URLs, 5-minute expiry |
| Loan | Applications, lifecycle state machine, agreements, schedules, late fees, funding saga | loan.*, repayment.* | Highest-criticality domain service |
| Marketplace | Listings, search, filters, direct offers, offer comparison | listing.*, offer.* | OpenSearch read model rebuilt from events |
| Investment | Lender preferences, commitments, auto-invest, holdings, portfolio performance | investment.\* | Auto-invest workers scale on Kafka lag |
| Risk | Risk grade A–E, debt-to-income, bureau data, fraud rules, model versioning | risk.assessed | Rules engine first; ML model later |
| Wallet | User wallets, holds, virtual accounts, verified bank accounts, limits | wallet.\* | Balance reads cached in Redis |
| Ledger | Double-entry journal for all money; balance source of truth | ledger.posted | Append-only; continuous balance check |
| Payment | PSP integration: top-ups, payouts, card and direct-debit repayments, webhooks, reconciliation | payment.\* | Idempotency keys; signed webhooks |
| Notification | Email, SMS, in-app, push; templates; user channel settings; reminders | notification.sent | Consumes events from all services |
| Messaging | Real-time lender–borrower chat, masking, abuse reports | message.sent | STOMP over WebSocket; Redis pub/sub across pods |
| Support | Tickets, replies, FAQs | ticket.updated | Can integrate a helpdesk tool later |
| Audit | Immutable record of state changes and admin actions | — | Write-once archive storage |
| Reporting | Analytics, regulatory and management reports, exports | — | ClickHouse fed from Kafka |

## 6. Data, events and API design

Each service owns its database and publishes its state changes as Kafka events through a transactional outbox; no service reads another service's tables.

### Data ownership

| Service | Datastore | Key entities |
| --- | --- | --- |
| Authentication | PostgreSQL | Users, credentials, roles, MFA, sessions |
| User Profile | PostgreSQL | Profile, employment, income, role flags, preferences |
| KYC | PostgreSQL | Verification case, BVN result, NIN result, liveness result, trust tier |
| Document | PostgreSQL + S3-compatible object storage | Document metadata, file pointer, checksum, scan status |
| Loan | PostgreSQL | Loan request, agreement, repayment schedule, instalment, state history |
| Marketplace | PostgreSQL + OpenSearch (read model) | Listing, offer, listing search index |
| Investment | PostgreSQL | Preference set, commitment, holding, auto-invest rule |
| Risk | PostgreSQL | Risk assessment, grade, model version, feature snapshot |
| Wallet | PostgreSQL + Redis (balance cache) | Wallet, hold, bank account, virtual account |
| Ledger | PostgreSQL (append-only) | Account, journal entry, posting |
| Payment | PostgreSQL | Payment intent, provider transaction, webhook log, reconciliation run |
| Notification | PostgreSQL | Template, notification, delivery attempt, user channel settings |
| Messaging | MongoDB | Conversation, message, report flag |
| Support | PostgreSQL | Ticket, ticket message, FAQ |
| Audit | PostgreSQL (append-only, partitioned) → archive to object storage | Audit record |
| Reporting | ClickHouse (or PostgreSQL read replica at launch) | Fact and dimension tables built from events |

Common data rules:

- Primary keys are UUID v7 (time-ordered); money is BIGINT kobo plus a currency code.
- Schema changes go through Liquibase migrations in each service repository, backward compatible for one release.
- PII columns (BVN, ID number, phone, DOB, address) are encrypted at field level with keys from the KMS/Vault; BVN is also stored as a salted hash for lookups.
- Soft delete only where retention rules require history; hard deletion on erasure requests where the law allows.

### Event catalogue (Kafka topics)

| Event | Producer | Main consumers |
| --- | --- | --- |
| user.registered | Authentication | User Profile, Wallet, Notification, Audit |
| kyc.verified / kyc.rejected | KYC | User Profile, Wallet, Notification, Risk, Audit |
| loan.submitted | Loan | Risk, Audit |
| risk.assessed | Risk | Loan, Marketplace |
| loan.listed | Loan | Marketplace, Investment (auto-invest), Notification |
| investment.committed / investment.cancelled | Investment | Marketplace, Loan, Wallet, Notification |
| loan.fully\_funded | Loan | Document (agreement), Notification |
| loan.agreement\_signed | Loan | Funding saga, Audit |
| loan.disbursed | Loan | Investment, Notification, Reporting |
| repayment.due / repayment.overdue | Loan (scheduler) | Notification, Risk |
| payment.succeeded / payment.failed | Payment | Wallet, Loan, Notification, Ledger |
| repayment.received | Loan | Investment, Ledger, Notification, Reporting |
| loan.repaid / loan.defaulted | Loan | Investment, Risk, Notification, Reporting |
| message.sent | Messaging | Notification |
| ticket.updated | Support | Notification |

Event rules:

- Envelope follows CloudEvents (id, source, type, time, subject, data); payloads are Avro, versioned in a schema registry with backward compatibility enforced.
- Topics are partitioned by aggregate id (loanId, userId) so events for one loan stay in order.
- Consumers are idempotent: processed event ids are recorded, so redelivery causes no double effect.
- Failed messages retry with backoff, then land in a dead-letter topic with an alert and a replay tool.
- Retention: 7 days on operational topics; compacted topics for reference data; Reporting keeps the history.

### Funding saga (orchestrated by the Loan Service)

The funding of a loan touches Investment, Wallet, Ledger and Payment, so it runs as an orchestrated saga with a compensation for every step.

1. Loan reaches Funded and the borrower signs → saga starts with a unique saga id.
2. Wallet converts every lender's hold into a debit → compensation: restore holds.
3. Ledger posts the journal: lender wallets → loan escrow account → borrower wallet, less origination fee → compensation: reversing journal.
4. Loan creates the repayment schedule and moves to Active → compensation: back to Funded with a failure reason.
5. Investment creates holdings for each lender's share.
6. loan.disbursed is published; notifications go out.

Any failure triggers compensations in reverse order, and the case goes to the admin queue if a compensation itself fails. Temporal is the recommended alternative if saga count and complexity grow.

### API conventions

- External: REST over HTTPS, JSON, OpenAPI 3.1 contracts generated with springdoc and checked in CI.
- Versioning in the path: /api/v1/…; breaking changes need a new version with 6 months' overlap.
- All money-moving POSTs require an Idempotency-Key header; duplicates return the original response.
- Errors use RFC 9457 Problem Details with a stable error code and a correlation id.
- Cursor-based pagination; filtering and sorting through query parameters.
- Internal service-to-service: REST with OpenFeign or gRPC for hot paths (Risk scoring, Wallet balance), always with timeouts, retries and circuit breakers.
- Real-time: WebSocket (STOMP) through the gateway for chat and in-app notifications.

### Sample endpoints

| Method and path | Purpose | Service |
| --- | --- | --- |
| POST /api/v1/auth/register | Sign up | Authentication |
| POST /api/v1/kyc/verifications | Start KYC with BVN, NIN and documents | KYC |
| PUT /api/v1/lenders/me/preferences/{id} | Save investment preferences | Investment |
| GET /api/v1/marketplace/listings?grade=A,B&minRate=18 | Browse listings | Marketplace |
| POST /api/v1/loans | Create loan application | Loan |
| POST /api/v1/loans/{id}/commitments | Commit funds to a loan | Investment |
| POST /api/v1/loans/{id}/accept | Accept offers and sign agreement | Loan |
| GET /api/v1/loans/{id}/schedule | Repayment schedule | Loan |
| POST /api/v1/loans/{id}/repayments | Make a repayment | Payment |
| GET /api/v1/wallets/me | Balance and holds | Wallet |
| POST /api/v1/wallets/me/withdrawals | Withdraw to bank | Payment |
| POST /api/v1/webhooks/{provider} | Provider callbacks (signature verified) | Payment |
| GET /api/v1/lenders/me/portfolio | Portfolio performance | Investment |
| POST /api/v1/support/tickets | Open a ticket | Support |

## 7. Technology stack and tooling

The backend standardises on Java 21 and Spring Boot 3.x with Spring Cloud; everything else is chosen to be open-source first, cloud-portable and well supported by the Spring ecosystem. Versions are pinned to the latest stable release at project start.

### Backend

| Layer | Choice | Why |
| --- | --- | --- |
| Language and runtime | Java 21 LTS (virtual threads enabled) | Long-term support; virtual threads simplify high-concurrency I/O |
| Framework | Spring Boot 3.x | Team standard; mature for fintech |
| Web | Spring Web MVC; Spring WebFlux only for the gateway and chat | Simpler debugging; reactive where streaming matters |
| Persistence | Spring Data JPA (Hibernate), Liquibase migrations | Standard ORM with versioned schema |
| Messaging | Apache Kafka, Spring for Apache Kafka, Confluent Schema Registry (Avro) | Durable, ordered, replayable events |
| Outbox / CDC | Transactional outbox table + Debezium connector | Atomic "save and publish" without dual writes |
| Service calls | Spring Cloud OpenFeign; gRPC (grpc-spring) for hot paths | Declarative clients; low latency where needed |
| Resilience | Resilience4j (circuit breaker, retry, bulkhead, rate limiter, timeout) | Contains failures between services |
| API gateway | Spring Cloud Gateway | Routing, JWT validation, rate limiting, request ids |
| Configuration | Spring Cloud Config (Git-backed) or Kubernetes ConfigMaps; secrets in HashiCorp Vault via Spring Cloud Vault | Central config; no secrets in code |
| Service discovery | Kubernetes DNS services (no Eureka needed on K8s) | Fewer moving parts |
| Security | Spring Security 6, OAuth2 Resource Server; Authentication Service issues and signs its own JWTs (OIDC-style) | Standards-based auth, MFA, RBAC out of the box |
| Mapping and validation | MapStruct, Jakarta Bean Validation | Compile-time mapping; consistent input rules |
| Scheduling | Spring Scheduler + ShedLock (distributed locks); Quartz for complex jobs | Repayment due checks run once across replicas |
| Saga orchestration | Custom orchestrator in Loan Service with Spring State Machine; Temporal as upgrade path | Explicit, auditable funding flow |
| Documents | OpenPDF or iText (agreements), Thymeleaf templates | Versioned agreement generation |
| API docs | springdoc-openapi (OpenAPI 3.1) | Contracts from code; client generation |
| Build | Maven (multi-module, Maven Wrapper) in a monorepo, with a parent POM, a shared BOM and a platform starter library | Consistent dependencies across services |

### Data and storage

| Need | Choice |
| --- | --- |
| Relational data | PostgreSQL 16 (one database per service; managed service such as Amazon RDS / Aurora) |
| Cache, sessions, rate limits, locks | Redis (managed, e.g. ElastiCache) |
| Marketplace search | OpenSearch |
| Chat messages | MongoDB (or PostgreSQL JSONB if the team prefers fewer engines) |
| Files and documents | Amazon S3 (or any S3-compatible store) with server-side encryption and ClamAV scanning |
| Analytics | ClickHouse; Metabase or Apache Superset for internal dashboards |

### Frontend

| Need | Choice |
| --- | --- |
| Web app | React with Next.js and TypeScript |
| UI | Tailwind CSS with a shared component library from the design system |
| State and data | TanStack Query; Zustand for local state |
| Forms | React Hook Form + Zod validation |
| Real time | STOMP over WebSocket client |
| Admin back-office | Separate Next.js app behind VPN / SSO, using the Admin BFF |
| Testing | Vitest, React Testing Library, Playwright end-to-end |

### Infrastructure and DevOps

| Need | Choice |
| --- | --- |
| Cloud | AWS (af-south-1 Cape Town region, or a Nigerian-hosted region if data-residency rules require it) |
| Containers | Docker images built with Jib or Buildpacks; distroless base images |
| Orchestration | Kubernetes (Amazon EKS) with Helm charts |
| Infrastructure as code | Terraform with remote state and per-environment workspaces |
| CI | GitHub Actions (or GitLab CI) |
| CD | Argo CD (GitOps) with Argo Rollouts for canary releases |
| Kafka | Amazon MSK or Confluent Cloud |
| Service mesh | Istio or Linkerd for mTLS between services (phase 2 if not at launch) |
| Ingress and edge | AWS ALB + AWS WAF + CloudFront for the web app |
| Secrets | HashiCorp Vault or AWS Secrets Manager; External Secrets Operator |

### Observability

| Need | Choice |
| --- | --- |
| Metrics | Micrometer → Prometheus → Grafana |
| Tracing | OpenTelemetry Java agent → Grafana Tempo (or Jaeger) |
| Logs | Structured JSON logs (Logback) → Loki (or ELK/OpenSearch) |
| Alerting | Alertmanager → PagerDuty or Opsgenie, Slack |
| Errors | Sentry for frontend and backend exceptions |
| Uptime | Synthetic checks on login, listing, repayment flows |

### Quality and security tooling

| Need | Choice |
| --- | --- |
| Unit and integration tests | JUnit 5, Mockito, AssertJ, Testcontainers (Postgres, Kafka, Redis) |
| Contract tests | Spring Cloud Contract or Pact between services |
| Performance | Gatling or k6 |
| Code quality | SonarQube, Spotless, Checkstyle, ArchUnit for module rules |
| Dependency and image scanning | OWASP Dependency-Check or Snyk, Trivy for images |
| DAST | OWASP ZAP in the staging pipeline |
| Secrets scanning | Gitleaks pre-commit and in CI |

### Third-party integrations (candidates, to be selected)

| Capability | Candidates in Nigeria |
| --- | --- |
| KYC, BVN and liveness | Smile ID, Dojah, Prembly (Identitypass), VerifyMe |
| Payments, virtual accounts, payouts | Paystack, Flutterwave, Monnify, a partner bank API |
| Direct debit mandates | Paystack, Mono, a NIBSS-connected provider |
| Bank statement analysis | Mono, Okra, Periculum |
| Credit bureau | CRC Credit Bureau, FirstCentral, CreditRegistry |
| SMS and OTP | Termii, Africa's Talking, Twilio |
| Email | Amazon SES, SendGrid, Postmark |
| Push (future mobile) | Firebase Cloud Messaging |

Every vendor sits behind a port-and-adapter interface in its service, so a provider can be swapped or run as a fallback without touching business logic.

## 8. Security, compliance and non-functional requirements

LendBridge handles identity data and other people's money, so security and compliance are launch blockers, not hardening tasks. The regulatory list below must be confirmed by Legal before build starts.

### Regulatory and compliance requirements

| Area | Requirement | How the platform meets it |
| --- | --- | --- |
| Data protection | Nigeria Data Protection Act 2023 and NDPC guidance; GDPR for any EU-resident users | Lawful basis and consent records, privacy notice, data subject request workflow (access, correction, erasure where allowed), DPIA before launch, Data Protection Officer named |
| Digital lending | FCCPC digital lending registration and consumer-protection rules (confirm current regulation) | Transparent pricing (APR and total cost shown before acceptance), no harassment in collections, no access to phone contacts, complaint handling within set timelines |
| Investment / crowdfunding | SEC Nigeria rules if the lender side is treated as an investment offering (confirm route) | Investor disclosures, risk warnings, limits per investor if required |
| AML / CFT | CBN AML rules, Nigerian Financial Intelligence Unit reporting | KYC tiers, sanctions and PEP screening, transaction monitoring rules, suspicious activity reports, record keeping |
| Payments | Funds held by a licensed partner (bank or PSP) | Segregated customer-money accounts; no customer funds in operating accounts |
| Card data | PCI DSS | Cards tokenised by the PSP; LendBridge never stores or transmits card numbers |
| Record keeping | Retain customer and transaction records for the legally required period (at least 5 years after relationship ends; confirm) | Audit Service archive with object lock |
| Legal records | Customer interactions stored for legal purposes | Chat, tickets, agreement versions and consents retained and exportable |

### Security controls

- **Identity and access**: Authentication Service as its own OAuth2/OIDC-style JWT issuer; MFA for all users before money movement; admin SSO with mandatory MFA and IP allow-listing; RBAC per endpoint; short-lived tokens.
- **Encryption**: TLS 1.2+ everywhere, mTLS between services; AES-256 at rest for databases and object storage; field-level encryption for PII; keys in KMS/Vault with rotation.
- **Application security**: OWASP ASVS Level 2 as the baseline; input validation; output encoding; CSRF protection on the web app; strict CORS and CSP.
- **Fraud controls**: velocity limits on sign-ups, loan requests and withdrawals; device fingerprinting; duplicate BVN and bank-account detection; rule engine to hold suspicious transactions for review.
- **Webhooks**: signature verification, replay protection by event id and timestamp, IP allow-list.
- **Secrets**: none in code or images; automatic rotation; Gitleaks in CI.
- **Supply chain**: signed images (Cosign), SBOM per build, base images patched monthly.
- **Testing**: annual external penetration test and before launch; bug bounty after launch.
- **Least privilege in data**: lenders see a borrower's verification badges and document checklist, never the raw documents, BVN or contact details.

### Non-functional requirements

| Category | Requirement |
| --- | --- |
| Availability | 99.9% monthly for core APIs (login, marketplace, wallet, repayment); multi-AZ for all stateful services |
| Performance | p95 < 300 ms reads, < 800 ms writes at 200 requests/second sustained; marketplace search p95 < 500 ms |
| Scalability | Horizontal scaling by HPA; designed for 100,000 users and 10,000 active loans in year 1 without re-architecture |
| Consistency | Ledger is strongly consistent (ACID); other read models eventually consistent within 5 seconds |
| Durability | RPO ≤ 5 minutes (point-in-time recovery); RTO ≤ 1 hour for core services |
| Backups | Daily snapshots, 35-day retention, cross-region copy; quarterly restore drill |
| Correctness | Zero tolerance for ledger imbalance: sum of debits equals sum of credits, checked continuously |
| Auditability | Every money movement and state change traceable by correlation id from API call to ledger posting |
| Accessibility | WCAG 2.1 AA for the web app |
| Compatibility | Last 2 versions of Chrome, Safari, Firefox, Edge; usable on low-end Android browsers and 3G speeds |
| Localisation | English at launch; dates, NGN currency and phone formats for Nigeria; text externalised for future languages |
| Maintainability | ≥ 80% unit-test coverage on domain logic; every service has a README, runbook and OpenAPI contract |

## 9. Delivery: DevOps, observability, testing, release plan and risks

Every service ships through the same automated pipeline into four environments, and nothing reaches production without passing tests, security scans and a canary.

### Environments

| Environment | Purpose | Data | Deploys |
| --- | --- | --- | --- |
| Local | Developer laptop with Docker Compose / Testcontainers | Synthetic | On demand |
| Dev | Shared integration of the latest main branch | Synthetic | Every merge |
| Staging | Production-like; provider sandboxes; performance and security testing | Anonymised or synthetic, never real PII | Every release candidate |
| Production | Live users and money | Real | Canary, then full rollout after approval |

### CI/CD pipeline (per service)

1. Pull request: build, unit tests, Spotless and Checkstyle, ArchUnit rules, SonarQube quality gate, Gitleaks.
2. Integration tests with Testcontainers (Postgres, Kafka, Redis) and contract tests against consumers.
3. Build the image, generate the SBOM, scan with Trivy, sign with Cosign, push to the registry.
4. Argo CD syncs to Dev automatically; end-to-end Playwright smoke tests run.
5. Promote to Staging by updating the GitOps repo; ZAP scan, k6/Gatling performance run, end-to-end suite.
6. Production: change approval for money-moving services, Argo Rollouts canary (5% → 25% → 100%) with automatic rollback on error-rate or latency alerts.
7. Database migrations run before the new version and stay backward compatible (expand, then contract).

### Observability and operations

- Golden signals (latency, traffic, errors, saturation) per service on Grafana dashboards, plus business dashboards: sign-ups, KYC pass rate, listings, funding rate, disbursements, repayments, overdue book.
- Every request carries a correlation id from the gateway through Kafka headers to the ledger posting.
- SLOs with error budgets for core APIs; alerts page the on-call engineer; runbooks linked from every alert.
- Financial alerts: ledger imbalance, reconciliation breaks, stuck sagas, dead-letter growth, payout failure spikes.
- On-call rotation, incident severity levels, blameless post-mortems within 5 working days.

### Testing strategy

| Level | What it proves | Tooling | Gate |
| --- | --- | --- | --- |
| Unit | Domain rules: interest, schedules, allocation, state transitions | JUnit 5, AssertJ | ≥ 80% domain coverage |
| Integration | Repositories, Kafka producers and consumers, outbox | Testcontainers | Must pass on PR |
| Contract | Services agree on APIs and events | Spring Cloud Contract / Pact, schema registry compatibility | Must pass on PR |
| End-to-end | Full journeys: sign-up → KYC → apply → fund → repay | Playwright, provider sandboxes | Must pass in Staging |
| Performance | NFR latency and throughput targets | k6 or Gatling | Before each major release |
| Security | No high or critical findings | ZAP, Trivy, Snyk, pen test | Before launch and yearly |
| Financial correctness | Ledger always balances; reconciliation matches provider | Property-based tests (jqwik), reconciliation replay | Every release |
| Resilience | Graceful degradation when a dependency fails | Chaos tests in Staging (pod kill, Kafka lag, provider timeout) | Quarterly |

### Team structure (suggested)

| Squad | Services owned |
| --- | --- |
| Identity and onboarding | Authentication, User Profile, KYC, Document |
| Marketplace | Loan, Marketplace, Investment, Risk |
| Money | Wallet, Ledger, Payment |
| Engagement | Notification, Messaging, Support, web frontend |
| Platform | Gateway, Admin BFF, Audit, Reporting, infrastructure, CI/CD, observability |

### Release plan

&#91;embedded content: release roadmap · 4 phases, 3 gates\]

Each gate is a go/no-go review with Product, Engineering, Risk and Legal; the closed beta caps loan sizes and user numbers until default and reconciliation KPIs hold for 4 consecutive weeks.

### Risks and mitigations

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| Regulatory route not settled before launch | Medium | High | Legal workstream starts in Phase 0; launch gated on licence or partner agreement |
| Borrower defaults erode lender trust | High | High | Strict KYC tiers, risk grades, exposure caps, transparent default stats, collections process |
| Money lost or double-counted across services | Low | Critical | Double-entry ledger, idempotency keys, sagas with compensation, daily reconciliation |
| Fraud: synthetic identities, stolen BVNs, money mules | Medium | High | Liveness, BVN-to-bank name match, device fingerprinting, velocity rules, manual review queue |
| Payment or KYC provider outage | Medium | Medium | Adapter pattern with a secondary provider; circuit breakers; queued retries |
| Microservices complexity slows the team | Medium | Medium | Shared platform starter library, templates, strong observability; start some services as modules in one deployable if headcount is short |
| Liquidity imbalance: too few lenders | Medium | High | Lender acquisition campaigns, auto-invest, partial funding, possible institutional lender partners |
| Data breach of PII | Low | Critical | Field-level encryption, least privilege, pen tests, incident response plan, breach notification within 72 hours |
