# ADR 0001: Use Liquibase for schema migrations

Date: 2026-10-21

## Status

Accepted

## Context

CLAUDE.md and the PRD originally specified Flyway for all services. When
scoping the Authentication Service (the first service to be built), the
build order note called for Liquibase changelogs instead. Asked directly,
the team chose to standardise on Liquibase project-wide rather than mix
tools between the first service and the rule.

## Decision

Every service uses Liquibase (YAML or XML changelogs) for schema
migrations, run on startup via `spring-boot-starter-data-jpa` +
`liquibase-core`. CLAUDE.md and the PRD have been updated accordingly.

## Consequences

- All later services copy the Liquibase changelog layout from
  Authentication Service's `src/main/resources/db/changelog`.
- No Flyway dependency is added to the BOM.
