# Product Definition

## Product

**Akumé Smart Storage**

## Problem

Physical components, cables, electronics, boards, tools, accessories, storage devices, and other objects become difficult to locate and manage as the Akumé environment grows.

Akumé Smart Storage provides structured identification, physical addressing, inventory tracking, movements, and printable labels.

The system also serves as a practical logistics and terminal-automation study environment.

## V1 objective

V1 must answer:

1. What items exist?
2. What physical addresses exist?
3. Where is an item currently stored?
4. How did it arrive at its current state?
5. How can an item or address be physically identified?

## V1 capabilities

### Address management

The user can:

- create address types;
- list address types;
- edit address types;
- activate/deactivate address types;
- create addresses;
- establish parent/child address relationships;
- list and inspect addresses;
- navigate the address hierarchy;
- activate/deactivate addresses.

Examples include room, cabinet, desk, drawer, shelf, box, rack, and compartment.

Address types are data, not a closed Java enum.

### Item management

The user can:

- create item categories;
- create items;
- list and inspect items;
- edit descriptive item information;
- activate/deactivate items.

Examples include cables, electronics, boards, adapters, power supplies, tools, storage devices, components, and utensils.

### Inventory

The system exposes the current stored state of items, including current location and quantity when applicable.

### Operations

V1 supports:

- `ENTRY`
- `EXIT`
- `INTERNAL_MOVEMENT`

Operations provide traceability for physical inventory transitions.

### Labels

The system can generate printable labels for items and addresses.

Labels contain human-readable identification and QR Codes.

Batch generation must be possible so multiple labels can be downloaded and printed.

### Frontend

The V1 user interface is implemented in Angular using PO UI.

It provides workflows for address types, addresses, categories, items, inventory consultation, operations, and labels.

## Explicitly outside V1

The following are not V1 requirements:

- AI/ML;
- automatic storage recommendation;
- address scoring;
- environmental compatibility;
- inherited storage restrictions;
- intelligent placement;
- complex authentication/authorization;
- microservices;
- event streaming;
- distributed architecture;
- warehouse optimization.

These capabilities may be evaluated in later milestones based on evidence obtained from V1 usage.

## V1 success scenario

The following workflow must be possible:

1. create an address hierarchy;
2. register an ESP32-S3;
3. execute an `ENTRY` operation placing it in a box;
4. execute an `INTERNAL_MOVEMENT` operation moving it to another address;
5. query the item and see the new current location;
6. inspect its operation history;
7. generate its printable QR label;
8. generate a printable QR label for an address;
9. execute an `EXIT`;
10. verify that the item is no longer stored internally.

This scenario is the principal functional acceptance path for V1.
