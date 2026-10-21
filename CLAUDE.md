# LendBridge – engineering rules

Product spec: docs/PRD.md (read the relevant sections before any task).

## Stack
- Java 21, Spring Boot 3.x, Spring Cloud, Maven (Maven Wrapper, ./mvnw) multi-module monorepo
  with a root aggregator POM, a parent POM for shared plugin/Java 21 settings, and a BOM for
  dependency versions
- PostgreSQL (one database per service), Flyway migrations
- Kafka with Avro + schema registry, transactional outbox pattern
- Keycloak for auth; services are OAuth2 resource servers
- Resilience4j, OpenFeign, springdoc-openapi, MapStruct
- Tests: JUnit 5, AssertJ, Testcontainers; contract tests for APIs and events

## Services (16 domain services + 2 edge components)
- Edge: api-gateway, admin-bff
- Onboarding: authentication-service, user-profile-service, kyc-service, document-service
- Lending: loan-service, marketplace-service, investment-service, risk-service
- Money: wallet-service, ledger-service, payment-service
- Engagement: notification-service, messaging-service, support-service
- Platform: audit-service, reporting-service

## Repo layout
- platform/bom – shared dependency versions
- platform/starter – shared library: error handling (RFC 9457), correlation ids,
  security config, outbox, idempotency, logging, OpenTelemetry
- services/<service-name> – one Maven module per service
- infra/docker-compose – local Postgres, Kafka, Schema Registry, Redis, Keycloak, MinIO
- docs/ – PRD, ADRs (docs/adr/), API notes

## Rules
- Each service owns its database; never read another service's tables.
- State changes publish events via the outbox; consumers must be idempotent.
- Money is stored as BIGINT kobo. Never use double or float for money.
- Only the Ledger Service moves money; wallet balances are derived from the ledger.
- Every money-moving POST requires an Idempotency-Key header.
- External providers (KYC, payments, SMS, email) sit behind interfaces, with a fake
  implementation for local development and tests.
- Package by feature: api / application / domain / infrastructure.
- Write tests with every change; run ./mvnw verify before saying a task is done.
- All application and test code is Java 21 with Spring Boot 3.x; do not use Kotlin.
- No secrets in code; use environment variables.
- Record architectural decisions as short ADRs in docs/adr/.
- Ask before adding a new library not listed above.
