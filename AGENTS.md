# Agent Engineering Contract

This file defines the operating rules for coding agents working on Akumé Smart Storage.

## 1. Scope discipline

Implement only the explicitly assigned task.

Do not implement future milestones, speculative abstractions, or unrelated refactors.

If the requested task conflicts with an architectural invariant, stop and report the conflict before modifying code.

## 2. Architecture

The backend follows:

- Domain-Driven Design;
- Hexagonal Architecture;
- Modular Monolith.

Domain code must not depend on:

- Spring;
- Spring Data;
- JPA/Hibernate;
- HTTP;
- PostgreSQL;
- Flyway;
- QR/PDF libraries;
- frontend concerns.

Framework and infrastructure dependencies belong in adapters or configuration.

## 3. Database

PostgreSQL is the relational database.

Flyway is the authority for database schema evolution.

Hibernate must not create or mutate the schema.

Expected policy:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Do not modify an already accepted/applied migration to introduce a new schema change. Create a new migration.

## 4. Inventory invariant

Physical inventory state must not be changed through arbitrary CRUD operations.

Once operation processing is introduced, physical changes to quantity or location occur through execution of an `Operation`.

The initial operation types are:

- `ENTRY`
- `EXIT`
- `INTERNAL_MOVEMENT`

Creating or editing an operation does not itself move inventory.

Execution performs the inventory transition.

## 5. Identifiers

Domain entities may use internal UUID identifiers.

Human-readable identifiers must remain distinct from internal database identity.

Examples:

```text
AKM-ADR-000001
AKM-ITEM-000001
AKM-OP-000001
```

Do not encode mutable business information into permanent identifiers.

## 6. Testing

Every task must introduce or update tests appropriate to its scope.

Prefer:

- domain unit tests for domain rules;
- application tests for use cases;
- integration tests for adapters and persistence;
- E2E tests for complete user workflows.

Tests must validate behavior rather than implementation details whenever practical.

## 7. Change discipline

Do not perform unrelated cleanup.

Do not rename public concepts without an explicit requirement.

Do not introduce a new framework, architectural pattern, database, messaging platform, or infrastructure dependency without authorization.

Do not create microservices.

Do not implement V2 functionality while working on V1.

## 8. Git safety

Unless explicitly requested:

- do not commit;
- do not push;
- do not open a pull request;
- do not approve a pull request;
- do not merge;
- do not modify remote branches.

Preserve unrelated existing worktree changes.

If pre-existing changes make the requested task unsafe, stop and report the condition.

## 9. Verification

Before declaring a task complete:

1. run the tests relevant to the changed scope;
2. run the appropriate build/verification command;
3. inspect the resulting diff;
4. verify that no unrelated files changed.

## 10. Completion report

Every completed task must report:

- task identifier;
- files created;
- files modified;
- implementation summary;
- tests executed;
- build/verification results;
- architectural decisions made;
- deviations from the specification, if any;
- risks or unresolved questions;
- repository/worktree state;
- confirmation that no commit, push, PR, or merge occurred unless explicitly authorized.

## 11. Stop conditions

Stop instead of guessing when:

- requirements materially conflict;
- an architectural invariant would need to be violated;
- existing repository state contradicts the expected baseline;
- destructive migration would be required without authorization;
- unrelated worktree changes would be overwritten;
- required external information is unavailable.

Report evidence and wait for a decision.
