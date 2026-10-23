# LendBridge – engineering rules

Product spec: docs/PRD.md (read the relevant sections before any task).

## Stack
- Java 21, Spring Boot 3.x, Spring Cloud, Maven (Maven Wrapper, ./mvnw) multi-module monorepo
  with a root aggregator POM, a parent POM for shared plugin/Java 21 settings, and a BOM for
  dependency versions
- PostgreSQL (one database per service), Liquibase migrations
- Kafka with Avro + schema registry, transactional outbox pattern
- Services are their own OAuth2/OIDC issuer where needed (JWTs signed and validated
  in-service, no external IdP); other services are OAuth2 resource servers against
  the issuing service's JWKS
- Resilience4j, OpenFeign, springdoc-openapi, MapStruct, Lombok
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
- infra/docker-compose – local Postgres, Kafka, Schema Registry, Redis, MinIO
- docs/ – PRD, ADRs (docs/adr/), API notes

## Rules
- Each service owns its database; never read another service's tables.
- State changes publish events via the outbox; consumers must be idempotent.
- Money is stored as BIGINT kobo. Never use double or float for money.
- Only the Ledger Service moves money; wallet balances are derived from the ledger.
- Every money-moving POST requires an Idempotency-Key header.
- External providers (KYC, payments, SMS, email) sit behind interfaces, with a fake
  implementation for local development and tests.
- Package by layer, not by feature: controller / service (+ service/impl) / repository / model
  (entities and enums together) / dto (flat, request and response DTOs together) / exception.
  Cross-cutting infrastructure (security, crypto, notification, outbox/Kafka, mapper, config)
  gets its own top-level package alongside those. Do not nest layer folders under a
  per-feature package.
- DTOs and entities are plain classes with Lombok (@Data, @Getter/@Setter, @AllArgsConstructor,
  @NoArgsConstructor, @Builder as fits) — no Java records, no hand-written getters/setters.
- Application-layer services are split into an interface (Xxx) and an implementation
  (XxxImpl annotated @Service) — never a single concrete service class. Service methods take
  the request DTO as a parameter rather than its fields expanded into separate arguments;
  transport-level context not in the DTO (IP, user-agent) stays as separate trailing params.
- Every successful controller response is wrapped in a generic ApiResponse<T> envelope
  (success/data/message/timestamp). Errors stay on RFC 9457 ProblemDetail — ApiResponse is
  success-path only.
- Write tests with every change; run ./mvnw verify before saying a task is done.
- All application and test code is Java 21 with Spring Boot 3.x; do not use Kotlin.
- No secrets in code; use environment variables.
- Record architectural decisions as short ADRs in docs/adr/.
- Ask before adding a new library not listed above.
