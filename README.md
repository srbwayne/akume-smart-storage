# Akumé Smart Storage

Akumé Smart Storage is a physical inventory and storage management system for the Akumé environment.

The project has two complementary purposes:

1. solve the practical problem of identifying, locating, organizing, and tracking physical items such as cables, electronic components, boards, adapters, tools, equipment, and accessories;
2. provide a controlled study environment for logistics and terminal-automation concepts using Java, Spring Boot, Angular, DDD, Hexagonal Architecture, PostgreSQL, Flyway, and operation-driven inventory movements.

## Core concept

Physical inventory state is changed through operations.

The initial operation types are:

- `ENTRY`
- `EXIT`
- `INTERNAL_MOVEMENT`

Items and physical addresses can be identified using printable labels and QR Codes.

## Technology baseline

### Backend

- Java 21
- Spring Boot
- Maven
- PostgreSQL
- Flyway
- DDD
- Hexagonal Architecture
- Modular Monolith

### Frontend

- Angular
- PO UI

## Repository structure

```text
backend/    Java/Spring application
frontend/   Angular/PO UI application
docs/       Product, domain and architecture documentation
```

## Development principle

Development is incremental.

Each task must:

1. have a bounded scope;
2. preserve architectural invariants;
3. introduce only what is necessary for its acceptance criteria;
4. include appropriate automated tests;
5. leave the repository in a verifiable state.

Future capabilities must not be implemented speculatively.

See:

- `docs/PRODUCT.md`
- `docs/DOMAIN.md`
- `docs/ARCHITECTURE.md`
- `docs/milestones/V1.md`
- `AGENTS.md`
