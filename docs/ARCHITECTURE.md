# Architecture

## Architectural style

Akumé Smart Storage uses:

- Modular Monolith;
- Domain-Driven Design;
- Hexagonal Architecture.

The architecture must support incremental development without introducing distributed-system complexity prematurely.

## Runtime structure

```text
Angular + PO UI
       │
       │ HTTP
       ▼
Spring Boot Application
       │
       ├── catalog
       ├── location
       ├── inventory
       ├── operation
       └── labeling
       │
       ▼
PostgreSQL
```

Module boundaries may evolve as domain knowledge improves, but changes must be explicit.

## Hexagonal direction

```text
Inbound Adapter
      ↓
Input Port
      ↓
Application
      ↓
Domain
      ↓
Output Port
      ↓
Outbound Adapter
```

Dependencies point toward domain/application abstractions.

Infrastructure must not dictate the domain model.

## Suggested module structure

```text
module/
├── domain/
│   ├── model/
│   ├── service/
│   └── exception/
├── application/
│   ├── port/
│   │   ├── in/
│   │   └── out/
│   └── service/
├── adapter/
│   ├── in/
│   │   └── rest/
│   └── out/
│       └── persistence/
└── configuration/
```

This is a structural guideline, not permission to create empty layers or abstractions before they are needed.

## Domain restrictions

Domain code must not import framework/infrastructure concerns such as:

```text
org.springframework.*
jakarta.persistence.*
```

Persistence entities and domain entities may be separated when required to preserve the boundary.

## Persistence

PostgreSQL is the V1 database.

Flyway owns schema evolution.

Hibernate validates mappings against the schema but does not create or update it.

## Transactions

Application use cases define transaction boundaries where persistence consistency requires them.

Operation execution and its inventory transition must eventually be atomic.

An operation must not become `EXECUTED` while only part of its inventory effects have been persisted.

## Frontend

The frontend uses Angular and PO UI.

Initial functional areas:

```text
core
shared
address-types
addresses
item-categories
items
inventory
operations
labels
```

Frontend organization should follow user capabilities rather than mirror backend implementation details mechanically.

## API

REST is the V1 application interface.

API contracts should expose domain/application semantics rather than persistence representations.

JPA entities must never be used directly as REST contracts.

## Testing strategy

Use the narrowest useful test layer.

### Domain
Fast unit tests without Spring.

### Application
Use-case tests with controlled ports.

### Infrastructure
Integration tests for PostgreSQL/Flyway/persistence where appropriate.

### API
HTTP contract/behavior tests.

### Frontend
Component/service tests and relevant integration tests.

### V1
End-to-end validation of the principal inventory workflow.

## Architectural invariant

Inventory mutation has a single business gateway:

```text
Operation execution
        ↓
Inventory transition
```

No alternate controller or repository path may bypass this invariant after the operation capability exists.
