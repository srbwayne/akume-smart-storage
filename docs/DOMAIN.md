# Domain Model

## Ubiquitous Language

### Item

A physical thing managed by Akumé Smart Storage.

An item describes what is being tracked.

Examples: Raspberry Pi, ESP32, SSD, USB-C cable, adapter, or tool.

Items may be individually tracked or quantity-based depending on future domain requirements. V1 must avoid unnecessary complexity while preserving this distinction conceptually.

### ItemCategory

Classifies an `Item`.

Categories are configurable data rather than a closed Java enum.

### Address

A physical storage location.

Addresses form a hierarchy.

Example:

```text
Home
└── Office
    └── Cabinet 01
        └── Shelf 02
            └── Box 03
                └── Compartment B
```

An address may have a parent address.

### AddressType

Describes the semantic type of an address.

Examples: room, cabinet, desk, shelf, drawer, box, rack, and compartment.

Address types are configurable.

### Operation

Represents an intentional physical inventory transition.

An operation has a lifecycle and one or more lines.

Creating an operation does not automatically alter physical inventory.

### OperationLine

Represents an item/quantity participating in an operation and the relevant source/destination information.

### OperationType

V1 defines three operation types.

#### ENTRY

Moves inventory from outside the managed storage environment into an internal address.

`EXTERNAL → ADDRESS`

Destination is required.

#### EXIT

Moves inventory from an internal address outside the managed storage environment.

`ADDRESS → EXTERNAL`

Source is required.

#### INTERNAL_MOVEMENT

Moves inventory between internal addresses.

`ADDRESS → ADDRESS`

Source and destination are required and must represent a meaningful movement.

### OperationStatus

Initial lifecycle:

`DRAFT → CONFIRMED → EXECUTED`

An operation may become `CANCELLED` where allowed by its lifecycle.

Detailed transition rules will be introduced with the operation milestone rather than speculated prematurely.

### Inventory

Represents the current physical storage state.

Inventory answers whether an item is currently stored, at which address, and in what quantity.

Inventory is current state. Operations provide the transition history that produced that state.

### Label

Printable physical identification associated with an item or address.

### QR Code

Machine-readable representation of a stable identity.

QR content must not depend on mutable descriptive information.

## Central invariant

Once operation execution exists:

> Physical inventory state is changed only as a consequence of executing an Operation.

Direct item CRUD must not silently alter physical storage state.

## Identity

Internal identity and human-readable codes are separate concepts.

Example:

```text
UUID:
7f...

Human code:
AKM-ITEM-000042
```

Human codes must remain stable after creation.

## Future domain concepts

Potential future concepts include placement policies, storage characteristics, restrictions, compatibility rules, recommendation, scoring, capacity, environment, and intelligent placement.

They are intentionally outside V1 unless promoted through a future explicit decision.
