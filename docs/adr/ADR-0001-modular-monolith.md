# ADR-0001 — Modular Monolith

## Status

Accepted

## Context

Akumé Smart Storage contains multiple business capabilities but is initially operated and developed as a single product.

Introducing independently deployed services would add operational and distributed-system complexity without evidence that it is required.

## Decision

The backend will be implemented as a modular monolith.

Business capabilities must maintain explicit internal boundaries while sharing a single application deployment.

## Consequences

Positive:

- simpler local development;
- simpler deployment;
- straightforward transactions;
- easier incremental evolution.

Constraint:

Module boundaries must not be ignored simply because modules execute in the same process.

Microservices require a future explicit architectural decision.
