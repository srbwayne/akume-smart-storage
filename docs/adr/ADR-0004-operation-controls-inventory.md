# ADR-0004 — Operations Control Inventory Transitions

## Status

Accepted

## Context

Akumé Smart Storage is intended to model physical inventory behavior similarly to logistics/terminal systems.

Directly editing an item's location would destroy the distinction between current state and the business event responsible for that state.

## Decision

Physical inventory transitions are performed through execution of an `Operation`.

V1 operation types are:

- `ENTRY`;
- `EXIT`;
- `INTERNAL_MOVEMENT`.

Creating, editing, or confirming an operation does not itself change inventory.

Execution applies the physical inventory transition.

## Invariant

```text
Operation execution
        ↓
Inventory mutation
```

There must not be an alternative application path that silently modifies physical location or stored quantity after operation processing is available.

## Consequences

The system can preserve:

- current inventory state;
- movement traceability;
- operation history;
- future auditability.

Operation execution and its resulting inventory changes must be transactionally consistent.
