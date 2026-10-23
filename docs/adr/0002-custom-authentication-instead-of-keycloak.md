# ADR 0002: Custom-built authentication instead of Keycloak or Spring Authorization Server

Date: 2026-10-21

## Status

Accepted

## Context

CLAUDE.md and the PRD originally named Keycloak as the identity provider,
with services acting as OAuth2 resource servers against it. When scoping
Authentication Service, the team reconsidered: Keycloak means running and
administering a separate server (realm config, admin API integration,
its own container in every environment) before the first line of product
code ships. Asked directly, the team first considered Spring
Authorization Server (an official Spring project that avoids a separate
server process but still handles OIDC flows, discovery, consent) as a
lower-risk alternative, then chose to go further: a fully custom-built
token service, owned entirely by Authentication Service.

## Decision

Authentication Service owns users, credentials, roles, MFA enrollments
and refresh-token sessions in its own PostgreSQL database, and issues and
validates its own JWTs (RS256, signed with `com.nimbusds:nimbus-jose-jwt`,
which is already pinned transitively via Spring Security's OAuth2 JOSE
support). It exposes a JWKS endpoint (`/.well-known/jwks.json`); every
other service validates tokens against that JWKS as a standard OAuth2
resource server, so the "services are OAuth2 resource servers" rule in
CLAUDE.md is unchanged — only the issuer changes, from Keycloak to our
own service.

Built responsibly rather than reinventing cryptography from scratch:
password hashing via Spring Security's `BCryptPasswordEncoder`; refresh
tokens are opaque random values, stored only as salted hashes, with
rotation and reuse-detection; TOTP (RFC 6238) and Base32 (RFC 4648) are
hand-rolled over `javax.crypto` rather than adding new libraries, and
validated against the RFCs' published test vectors.

## Consequences

- No Keycloak container in `infra/docker-compose`, no realm
  configuration, no `keycloak-admin-client` dependency.
- Authentication Service carries more responsibility and more security
  surface than a thin Keycloak wrapper would: token issuance, MFA,
  lockout, rotation and revocation are all our own code and our own risk,
  not delegated to a battle-tested IdP. This is a deliberate trade
  accepted by the team in exchange for operational simplicity.
- **Known v1 gap**: field-level encryption (phone number) uses a
  symmetric key read from an environment variable, not a real KMS/Vault
  — call this out again once real secrets infrastructure exists, since
  CLAUDE.md requires "no secrets in code" but does not yet specify a key
  management service.
- No access-token revocation list exists yet (mitigated by a 15-minute
  access-token lifetime); revisit if a faster-than-15-minute revocation
  requirement appears (e.g. a confirmed account compromise).
