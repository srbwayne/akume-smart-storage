# ADR-0003 — Flyway as Schema Authority

## Status

Accepted

## Decision

PostgreSQL is the V1 relational database.

Flyway migrations are the authoritative mechanism for schema evolution.

Hibernate schema generation/update is disabled.

Hibernate may validate mappings against the schema.

## Invariant

Application startup must never depend on Hibernate implicitly creating production schema structures.

Schema changes require explicit versioned migrations.
