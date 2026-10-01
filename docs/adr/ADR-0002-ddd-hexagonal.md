# ADR-0002 — DDD and Hexagonal Architecture

## Status

Accepted

## Context

The project is both a useful application and an environment for studying logistics/terminal-automation domain modeling.

Business rules must therefore remain explicit and independently testable.

## Decision

The backend uses Domain-Driven Design and Hexagonal Architecture.

Domain logic remains independent from Spring, HTTP, persistence, and external libraries.

Application use cases interact with external concerns through ports and adapters.

## Consequences

Infrastructure can evolve without becoming the domain model.

The project accepts some additional structural discipline in exchange for clearer boundaries and testability.

Unnecessary abstractions must still be avoided.
