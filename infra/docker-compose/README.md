# Local dev infra

Postgres, Kafka, Schema Registry, Redis and LocalStack (S3) for running services locally.

```
docker compose -f infra/docker-compose/docker-compose.yml up -d
```

The Postgres container only auto-creates one database/role via its env vars:
`authentication_db` / `authentication_service`. Every other service owns its own database
(CLAUDE.md: "Each service owns its database"), so its role and database need to be created once,
by hand, before that service will start — each one in `application-local.yml` has the pattern
`<service>_db` / `<service>_service`, password `local-dev-only-password`:

```
docker exec -i docker-compose-postgres-1 psql -U authentication_service -d postgres <<'SQL'
CREATE ROLE document_service LOGIN PASSWORD 'local-dev-only-password';
CREATE DATABASE document_db OWNER document_service;

CREATE ROLE ledger_service LOGIN PASSWORD 'local-dev-only-password';
CREATE DATABASE ledger_db OWNER ledger_service;

CREATE ROLE payment_service LOGIN PASSWORD 'local-dev-only-password';
CREATE DATABASE payment_db OWNER payment_service;

CREATE ROLE user_profile_service LOGIN PASSWORD 'local-dev-only-password';
CREATE DATABASE user_profile_db OWNER user_profile_service;

CREATE ROLE wallet_service LOGIN PASSWORD 'local-dev-only-password';
CREATE DATABASE wallet_db OWNER wallet_service;

CREATE ROLE kyc_service LOGIN PASSWORD 'local-dev-only-password';
CREATE DATABASE kyc_db OWNER kyc_service;

CREATE ROLE loan_service LOGIN PASSWORD 'local-dev-only-password';
CREATE DATABASE loan_db OWNER loan_service;
SQL
```

Add the same two lines for any new service's database as it's added. If you have a local Postgres
of your own (Postgres.app, Homebrew, etc.) also listening on 5432, stop it first — the container
above binds the same host port.

Run authentication-service against it:

```
SPRING_PROFILES_ACTIVE=local ./mvnw -pl services/authentication-service -am spring-boot:run
```

Liquibase applies migrations automatically on startup. Once it's up, Swagger UI is at
http://localhost:8081/swagger-ui.html (authentication-service listens on 8081, not the Spring
Boot default 8080, so api-gateway can own 8080 as the public entry point).

Run api-gateway (needs authentication-service already running, for JWKS lookup and routing):

```
./mvnw -pl services/api-gateway -am spring-boot:run
```

Then hit the same endpoints through the gateway at http://localhost:8080/api/v1/auth/....

Run user-profile-service (listens on 8082):

```
SPRING_PROFILES_ACTIVE=local ./mvnw -pl services/user-profile-service -am spring-boot:run
```

Run document-service (listens on 8083; needs authentication-service for JWKS and LocalStack for
S3-compatible storage, exposed at http://localhost:4566); document-service creates its own bucket
on startup, nothing to set up by hand:

```
SPRING_PROFILES_ACTIVE=local ./mvnw -pl services/document-service -am spring-boot:run
```

Run ledger-service (listens on 8090; needs authentication-service for JWKS):

```
SPRING_PROFILES_ACTIVE=local ./mvnw -pl services/ledger-service -am spring-boot:run
```

Run wallet-service (listens on 8091; needs authentication-service for JWKS, ledger-service for
balances, and Redis for the ledger-balance cache — bring ledger-service up first, since wallet
balances are derived from it):

```
SPRING_PROFILES_ACTIVE=local ./mvnw -pl services/wallet-service -am spring-boot:run
```

Run payment-service (listens on 8092; needs authentication-service for JWKS and wallet-service for
top-ups/withdrawals — bring wallet-service up first). Real PSP calls (Paystack, Flutterwave,
Monnify) are disabled locally by default (`payment.provider.primary`/`secondary` default to
`fake`); set the relevant `PAYSTACK_*`/`FLUTTERWAVE_*`/`MONNIFY_*` env vars and provider names to
exercise a real adapter:

```
SPRING_PROFILES_ACTIVE=local ./mvnw -pl services/payment-service -am spring-boot:run
```

Run kyc-service (listens on 8093; needs authentication-service for JWKS and document-service for
fetching the uploaded ID card/selfie/bank-statement documents it verifies). Real BVN/NIN/liveness
calls (Smile Identity, Dojah, Prembly) are disabled locally by default
(`kyc.provider.primary`/`secondary` default to `fake`); see the commented-out block in
`application-local.yml` for how to switch one on once API keys are issued:

```
SPRING_PROFILES_ACTIVE=local ./mvnw -pl services/kyc-service -am spring-boot:run
```

Run loan-service (listens on 8094; needs authentication-service for JWKS, kyc-service for trust-tier
checks, and document-service for agreement PDF uploads — bring both up first). Phase 1 only: no
funding, disbursement or repayment yet — see docs/adr/0015-loan-service-phase-1-scope.md:

```
SPRING_PROFILES_ACTIVE=local ./mvnw -pl services/loan-service -am spring-boot:run
```
