# M2 — Address Hierarchy Design Contract

Status: proposed design contract for M2 implementation planning. This document does not authorize production implementation.

## 1. Baseline

- Branch: `main`
- M1 commit: `6c0407d8630a8de27a40f729737050c9d20fe8d2`
- M1 status: complete
- Existing model: `AddressType` is a configurable domain entity with UUID identity, immutable business `code`, mutable name/description, and explicit active lifecycle.
- Existing architecture: Spring Boot modular monolith, DDD and hexagonal boundaries; PostgreSQL; Flyway owns schema; Hibernate uses `ddl-auto: validate`; REST DTOs are distinct from JPA entities.
- No tenant/workspace concept or Item module exists in this baseline.

## 2. Objective and terminology

An **Address** is one concrete physical location, such as `Casa`, `Escritório`, `Armário A`, or `Gaveta 01`. An **AddressType** classifies what kind of location it is, such as `ROOM`, `CABINET`, or `DRAWER`. The type is descriptive classification, not a rule that determines which parent types are legal.

An Address has zero or one parent and zero or more children. A root is an Address whose `parentId` is null. The Address records its own parent relation; children are obtained by query and are not an in-memory collection that must be loaded to edit one node.

## 3. Frozen invariants

The following rules are product constraints for M2:

1. `parentId` is optional. Multiple roots are valid.
2. Parent/child links are structural. They remain in place across activation changes unless a user explicitly moves the Address.
3. Deactivation affects one Address only. It never cascades and never promotes or reparents descendants.
4. An Address with an active direct child cannot be deactivated (`ADDRESS_HAS_ACTIVE_CHILDREN`). Deactivate an entire structure explicitly from leaves toward the root.
5. An Address containing an Item directly cannot be deactivated (`ADDRESS_NOT_EMPTY`). This is a **FROZEN FUTURE-INTEGRATION INVARIANT**. There is no Item module in this baseline; M2 must not invent one or add an artificial occupancy port solely for speculative use. When Item exists, the rule must be enforced in the application boundary using a concrete Item capability.
6. Moving a node changes only that node's `parentId`. Its descendants remain attached, and it does not individually move or rewrite them.
7. An Address cannot be its own parent and cannot be moved below any of its descendants.
8. An active Address can only be created or moved under an active parent. An active Address cannot be reactivated when its parent is inactive. A root has no parent constraint.
9. No normal hard-delete operation exists. History/topology is retained through `ACTIVE ↔ INACTIVE`.
10. Item movement and Address movement are distinct future use cases. Moving an Address reorganizes a subtree; it is not a substitute for an inventory operation that moves an Item.
11. Type compatibility rules (for example, a Drawer only under a Cabinet) are **DEFERRED**. Initial M2 validates generic tree integrity only.

### Inactive children and deactivation

An inactive direct child does not by itself block deactivation of its parent. The inactive child remains linked beneath that now-inactive parent. It is not promoted. Since active children are prohibited, an inactive parent cannot contain active direct children after a successful deactivation. Activation is still explicit and checks the parent and type prerequisites.

## 4. Address conceptual model

```text
AddressType (existing)
      ↑ addressTypeId (required reference)
      │
Address ── parentId? ──> Address
   │                         │
   └── children (query) <────┘

Future only:
Item ── locatedAtAddressId ──> Address
```

Recommended initial fields:

| Field | Purpose / requirement | Mutability and validation |
|---|---|---|
| `id: UUID` | Stable internal identity, required, generated at creation following the M1 identity convention. | Immutable. Never derived from the name/path. |
| `addressTypeId: UUID` | Required reference to the semantic type. | Immutable through ordinary M2 edit. Must reference an existing active AddressType at creation. |
| `parentId: UUID?` | Optional structural parent; null means root. | Mutable only through explicit move. Must reference an existing Address; self/cycles forbidden. |
| `name: String` | Human-readable local label, required. Names such as `Gaveta 01` are meaningful in the context of a parent. | Mutable; trim outer whitespace; reject null/blank. See sibling uniqueness decision below. |
| `active: boolean` | Whether the location is available for active use. | Defaults true; changes only through explicit lifecycle operations. |
| `version: integer` | Persistence concurrency token. | Infrastructure-managed and returned to clients as an ETag/version token; clients do not choose its value. |

Do **not** introduce an Address business `code` in initial M2. The baseline gives AddressType a stable human code because it is reusable configuration; there is no established Address-code format or label/path requirement that justifies another identifier. UUID is sufficient for API identity. If labels or external integrations later demonstrate a need for a human Address code, decide that separately and keep it distinct from mutable name/path.

Do not add `createdAt`/`updatedAt` to the domain or persistence model without a concrete audit/UX requirement. If operational audit becomes required, define its retention and actor semantics rather than adding timestamps by habit. The initial design has no tenant/workspace field because none exists in the baseline; do not add one speculatively.

## 5. Root, names, and uniqueness decisions

### Roots

Allow multiple roots (for example, `Casa`, `Escritório externo`, `Depósito`). There is no synthetic `ROOT` AddressType or technical root node. A root is simply `parentId = null`.

### Name policy

Recommend **unique names among siblings**, including roots as one sibling set. This allows `Gaveta 01` under both `Escritório` and `Quarto` while preventing two visually indistinguishable siblings under one parent. Enforce uniqueness across active and inactive siblings so a rename/create cannot take a name needed by an inactive sibling and reactivation cannot surface a collision.

The accepted display name is `input.strip()` using Java `String.strip()` and its `Character.isWhitespace` definition. Reject the input if the stripped display name is empty. Preserve the stripped spelling for display; do not normalize the stored display spelling to enforce uniqueness. Internal whitespace and punctuation remain unchanged.

Freeze the canonical sibling-name key algorithm as:

```java
String displayName = input.strip();
String normalizedNameKey = Normalizer.normalize(
    Normalizer.normalize(displayName, Normalizer.Form.NFC)
        .toLowerCase(Locale.ROOT),
    Normalizer.Form.NFC
);
```

Use Java `Locale.ROOT`; never use the default JVM locale. This applies Unicode NFC, locale-independent Java lowercase, and NFC again. Preserve accents: do not accent-fold, transliterate, collapse internal whitespace, or remove punctuation. Thus `Drawer` and `drawer` collide; `" Sala "` and `Sala` collide; composed/decomposed `Café` collide after NFC; `Armário` and `armario` do not collide; and `Sala  Principal` remains distinct from `Sala Principal`. This contract intentionally specifies Java's lowercase behavior rather than promising broader Unicode full-case-fold equivalence.

Persist the application-generated `normalized_name_key`. The application/domain algorithm above is the semantic authority for constructing it; PostgreSQL enforces uniqueness using exact equality of the persisted key. Do not use PostgreSQL `lower(name)` or collation behavior to independently define the key. M2-02 chooses the PostgreSQL constraint/index form, including correct handling for roots whose `parent_id` is NULL. Root uniqueness is required; the exact index strategy is not frozen here.

Create and rename must derive display name and key with this same algorithm. Move does not change the key, but must check destination sibling conflicts by that key; the database unique constraint remains the final race-safe enforcement. Application and PostgreSQL agree by storing the canonical key once and applying exact database uniqueness to it, rather than implementing two Unicode transformations.

This recommendation is preferable to global uniqueness (which rejects valid repeated local labels), unrestricted duplicate names (which makes sibling selection ambiguous), or introducing a separate code with no current use case.

## 6. Create and edit contract

### Create

Creation accepts `addressTypeId`, optional `parentId`, and `name`. It creates one active Address by default, consistent with M1's create-active convention. The type must exist and be active. If `parentId` is supplied, the parent must exist; an active child requires an active parent. Creation does not accept `id`, `active`, version, path, or children from the caller.

Creating a root is the same operation with `parentId = null`. It is not an orphaning side effect.

### Edit

Ordinary update changes `name` only. It cannot change `id`, `parentId`, `addressTypeId`, or `active`. A correction to the selected type is not an ordinary edit; type immutability preserves the meaning/history of the location and avoids future compatibility and Item ambiguity. If a real correction workflow is needed, define an explicit, audited operation in a later decision.

## 7. Move semantics and cycle prevention

Move is an explicit operation `move(addressId, newParentId?)`:

- A UUID parent changes only the selected Address's `parentId`.
- A null parent is an explicit **MOVE TO ROOT** and is valid. It is not an orphan caused by deletion/deactivation.
- Moving to the current parent is an idempotent successful no-op, including root-to-root. It performs no persistence write and does not increment the Address version solely because of the request. Normal resource and expected-version checks may occur before recognizing the no-op. It cannot be used to rename, activate, or otherwise mutate the Address.
- Moving beneath self is rejected (`ADDRESS_CANNOT_BE_OWN_PARENT`).
- Moving beneath any descendant is rejected (`ADDRESS_CYCLE_DETECTED`).
- A moved active Address requires an active destination parent. An inactive Address may be reorganized while inactive; moving it does not activate it. It may be placed under an inactive parent, but cannot be activated until prerequisites are met.
- The target parent and Address must exist. Parent and destination subtree are otherwise retained; no descendant update is issued.
- AddressType does not change as a side effect of move.

Cycle detection should query the persisted ancestor/descendant relation, not load the whole tree into an aggregate. On PostgreSQL, an indexed recursive CTE over `parent_id` can determine whether the proposed parent is the node itself or lies below it. Use a visited set/path guard in the query as defense against pre-existing corrupt data. Add an index on `parent_id`. The exact query belongs to the persistence adapter; domain code remains PostgreSQL-independent.

## 8. Lifecycle contract

### Deactivate one Address

`deactivate(addressId)` rejects when any direct child is active (`ADDRESS_HAS_ACTIVE_CHILDREN`) and, once Item exists, when any Item is directly stored there (`ADDRESS_NOT_EMPTY`). It does not inspect-and-deactivate descendants, detach children, or update the tree recursively. Inactive direct children remain structurally attached and do not block under the frozen active-child rule.

Lifecycle transitions are idempotent, consistent with M1: activating an already-active Address and deactivating an already-inactive Address succeed as no-ops. A no-op makes no persistence write, does not increment version, does not cascade, and does not change structural links. Idempotency does not bypass validation of the resulting invariant: activation still requires an active AddressType and active parent where one exists, even if persisted state already says active. Deactivating an active Address still checks for active direct children; future direct Item occupancy also remains a blocker. Deactivating an already-inactive Address does not inspect or mutate children.

### Activate one Address

`activate(addressId)` is explicit and single-node. It requires an active AddressType and, if non-root, an active parent. It does not activate the parent, children, or any subtree. In particular, a child under an inactive parent remains inactive until the parent is explicitly activated, then the child may be activated separately.

### AddressType lifecycle integration

Recommendation: reject deactivation of an active AddressType while any active Address references it, with a semantic conflict such as `ADDRESS_TYPE_IN_USE`. Do not silently deactivate or mutate Addresses. Inactive Addresses may retain the reference when the type becomes inactive. Reactivating an Address requires its AddressType to be active, so reactivation of retained locations follows explicit type reactivation first.

Address creation requires an active AddressType. Ordinary Address edits cannot change type. Persist `address_type_id` as a foreign key to the existing `address_types.id`; it is a reference by UUID, not ownership or cascade lifecycle. The AddressType module must not own or cascade-persist Address instances. M2 must coordinate the usage check with concurrent Address creation and type deactivation so an active Address cannot be created against a type at the same time it is successfully deactivated.

This is a recommendation to freeze for M2: it preserves M1's explicit lifecycle, prevents active locations from being classified by unavailable configuration, and avoids hidden cascades. M1 currently permits AddressType deactivation without an Address-usage check because Address does not yet exist; M2 must add the cross-module application rule without changing the meaning of AddressType's domain entity.

## 9. Future Item integration

The future relationship is conceptually `Item.locatedAtAddressId → Address.id`. Address does not contain a collection of Items. The Item capability owns item location/inventory semantics. The future deactivation rule asks an Item/inventory query whether an Item is stored **directly** at the Address; it does not count an Item in descendants as being stored directly in an ancestor. Since parent deactivation is independently blocked while an active direct child exists, users can work leaf-first and clear direct Item occupancy at each leaf.

Do not create Item code, schema, endpoints, stock accounting, or Item-move behavior in M2. A future operation that moves an Item between locations is different from `move(Address)` and remains governed by future inventory/operation milestones.

## 10. Aggregate boundary

Recommend each Address as an individual aggregate root. A house-wide tree is not one aggregate: renaming a drawer must not load or save an entire house. An Address aggregate owns local invariants (`id`, required type reference, nonblank name, active state); hierarchy facts requiring other nodes are checked by application services through output ports/queries in a transaction.

The aggregate does not own `children`, AddressType, or Items. Application use cases coordinate the Address, queries, and persistence boundary. REST controllers call input ports; they do not call repositories/JPA directly. Domain objects stay free of Spring/JPA/HTTP types. Persistence entities do not become REST responses.

## 11. Persistence concept (not final SQL)

Suggested `addresses` table:

- UUID primary key;
- required `address_type_id` FK to `address_types(id)`, delete restricted;
- nullable `parent_id` self-FK to `addresses(id)`, delete restricted;
- required display `name`;
- a deterministic normalized sibling-name key or equivalent uniqueness mechanism;
- required `active` boolean;
- required optimistic `version` integer.

Indexes: `parent_id` for children/recursive traversal; `address_type_id` and active/type combinations as query plans justify; unique normalized sibling-name constraints for both non-root siblings and root siblings. A self-parent `CHECK (parent_id <> id)` is appropriate. A CHECK cannot enforce arbitrary transitive acyclicity; cycle prevention belongs in the transactional application/persistence query and must be protected against concurrent topology writes.

There is no tenant column because the baseline has no tenant concept. Do not add timestamps absent an audit requirement. Flyway must add a new migration; never edit accepted migrations. Hibernate remains `ddl-auto: validate` and cannot create/update the schema. No hard-delete route or cascade-delete relation is introduced.

## 12. Concurrency strategy

Concurrency must protect these concrete races:

- adding an active child while deactivating its parent;
- creating an active Address while deactivating its AddressType;
- two simultaneous moves that would together form a cycle;
- moving a child while its ancestor/subtree is being moved;
- stale rename/move/lifecycle requests overwriting newer state.

Recommend an optimistic `version` on Address and require the expected version (HTTP `If-Match`/ETag or equivalent request version) for updates, moves, and lifecycle mutations. A stale version returns `409 ADDRESS_CONCURRENT_MODIFICATION` (or standard `412` if the API adopts HTTP conditional request semantics consistently).

Version alone does not protect graph predicates/phantoms. For initial PostgreSQL M2, perform structural writes (create child, move, activate/deactivate) in a transaction using `SERIALIZABLE` isolation, re-read/validate after conflicts, and translate exhausted serialization conflicts into a safe conflict response. Verify the exact Spring transaction behavior and PostgreSQL retry boundary during implementation; never retry an already partially committed business operation. AddressType deactivate and Address creation must participate in a compatible lock/serialization protocol on the referenced type row. If measured/tested implementation complexity makes serializable isolation unsuitable, revise this decision explicitly before coding; do not silently rely on an in-memory cycle check or per-row `@Version` for graph safety.

## 13. REST concept

Follow M1's `/api/...` naming, UUID path identity, request/response DTO separation, semantic errors, and explicit lifecycle actions. Candidate contracts:

```text
POST   /api/addresses
GET    /api/addresses/{id}
GET    /api/addresses?parentId={uuid}&active={boolean}&addressTypeId={uuid}
GET    /api/addresses/roots
GET    /api/addresses/{id}/children
PUT    /api/addresses/{id}                 # name only, conditional version
POST   /api/addresses/{id}/move             # new parent UUID or null
POST   /api/addresses/{id}/activate
POST   /api/addresses/{id}/deactivate
```

These are conceptual routes, not frozen endpoint implementation. A flat administration list must not depend on ordering; the service may define stable name ordering for UI convenience once explicitly specified. `roots` and `children` allow lazy tree navigation without requiring one unbounded full-tree response. Child/roots reads should have a documented maximum page size if datasets grow; do not emulate pagination in the frontend.

Responses should contain `id`, `addressTypeId` (and optionally type display summary), `parentId`, `name`, `active`, and version/ETag. They must not contain a persisted path field. Create accepts `addressTypeId`, `parentId?`, and `name` only. Update accepts `name` and version/conditional header only. Move accepts a nullable `parentId` plus version. Lifecycle endpoints express state transitions; generic PUT cannot change `active`, type, or parent.

Semantic client errors should include at least not found, invalid input, and conflicts such as `ADDRESS_HAS_ACTIVE_CHILDREN`, `ADDRESS_NOT_EMPTY` (future), inactive parent/type, cycle, sibling-name conflict, AddressType in use, and stale/concurrent modification. Messages must not expose SQL, constraint names, or stack traces.

## 14. Tree read model and derived path

Provide separate administrative flat/list reads and hierarchy navigation reads. Initial navigation can load roots, then request each node's children on expansion. Responses can include `hasChildren`/child count to communicate affordances without loading every descendant. Avoid a single unlimited tree document; allow bounded depth/page behavior if a full-tree view is later justified.

Display paths such as `Casa / Escritório / Armário A / Gaveta 02` as a derived read model. Do not persist a path string: moving a parent would require rewriting all descendants and create duplicated state. A path query can use a recursive CTE. For a selected detail/list, compute paths only where needed and consider query cost/maximum depth; do not make every write load or persist descendant paths. A future materialized read model is a separate optimization requiring evidence.

## 15. First Angular/PO UI concept

The first Address workflow should provide an `Endereços` page with:

- a lazy hierarchy tree and a flat/list view for administration;
- `Nova localização` with an explicit choice: create root or create child under the selected parent;
- fields for name and active AddressType, with parent selection for child creation;
- detail/edit of name, with type and parent displayed read-only outside the explicit move flow;
- explicit `Mover` action using a parent selector that includes “Raiz”; exclude the current node and descendants from valid destinations when possible, while leaving server validation authoritative;
- explicit `Ativar`/`Desativar` actions and status labels;
- no drag-and-drop in initial M2.

On rejected deactivation, show the semantic reason in user language, for example: “Não é possível desativar ‘Armário A’. Existem localizações ativas dentro dele.” When the future Item check applies: “Ainda existem itens armazenados nesta localização.” Do not silently disable the button based on incomplete client state; server validation is authoritative and its conflict must remain visible. Move and activation are separate user actions.

## 16. Consistency matrix

| Operation / state | Decision |
|---|---|
| Create root | Allowed; `parentId = null`; multiple roots allowed. |
| Create active child under active parent | Allowed if AddressType is active and name is valid/unique among siblings. |
| Create active child under inactive parent | Rejected (`ADDRESS_PARENT_INACTIVE`). |
| Create with inactive AddressType | Rejected (`ADDRESS_TYPE_INACTIVE`). |
| Create under missing parent/type | Rejected as not found. |
| Update name | Allowed with validation/version; no changes to type, parent, active. |
| Update code | No Address code in initial contract. |
| Change AddressType | Prohibited by ordinary M2 update. |
| Move to another active parent | Allowed if no cycle and sibling-name uniqueness holds at destination. |
| Move active Address to inactive parent | Rejected. |
| Move inactive Address | Allowed; it remains inactive. Destination may be inactive; reactivation has stricter checks. |
| Move to root | Allowed explicitly; preserves descendants. |
| Move under self | Rejected. |
| Move under descendant | Rejected using persisted hierarchy query. |
| Deactivate empty leaf | Allowed. |
| Deactivate with active child | Rejected; no cascade. |
| Deactivate with only inactive children | Allowed; links remain intact under inactive parent. |
| Deactivate with directly stored Item | Rejected when Item integration exists; frozen future invariant. |
| Activate root | Allowed if AddressType is active. |
| Activate child with active parent | Allowed if AddressType is active. |
| Activate child with inactive parent | Rejected; parent must be explicitly activated first. |
| Activate when AddressType is inactive | Rejected; AddressType must be explicitly activated first. |

## 17. Decisions taken and deferred

### Recommended decisions for M2 freeze

- Address is a single-node aggregate root with UUID identity.
- Multiple roots are allowed; move-to-root is an explicit valid move.
- No Address business code, timestamps, tenant field, or persisted path in initial M2.
- Name is unique among siblings (including roots and inactive records), using the frozen `strip` → NFC → `Locale.ROOT` lowercase → NFC canonical key; PostgreSQL uniquely constrains the persisted key.
- AddressType and parent are immutable through generic update; parent changes only through move.
- Same-parent move and already-satisfied lifecycle requests are successful no-ops without persistence writes or version increments; normal precondition/version validation still applies where specified.
- Inactive Addresses may be moved; move does not activate.
- Active child requires active parent at create, move, and activation.
- AddressType must be active for create/activation; active AddressType in use cannot be deactivated.
- Deactivation is one-node/no-cascade and blocked by active direct children; future direct Item occupancy blocks as well.
- A version token protects stale writes; PostgreSQL serializable transactions protect cross-row tree/type predicates in initial M2, pending implementation validation.
- Flat reads and lazy hierarchy reads are distinct; derived paths are not stored.

### Deferred

- Type compatibility rules: **DEFERRED**.
- Item module, Item table, occupancy port, Item API, and Item movement: outside M2.
- Human-readable Address code: defer until a concrete label/integration requirement exists.
- Manual sibling ordering/`position`/`sortOrder`: defer; initially display siblings by name with a stable, documented ordering.
- Full-tree eager endpoint, materialized path, closure table, nested set, and tree cache: defer until measurement justifies them.
- Hard delete, subtree delete/deactivate, automatic child promotion, and automatic type change: not part of M2.
- Tenant/workspace partitioning and audit timestamps: defer because the baseline has no corresponding concept/requirement.
- Exact error code naming and exact routes are finalized with REST task while preserving these semantics.

## 18. Implementation sequence

1. **M2-01 — Address domain model and local invariants.** Add the Address domain model and unit tests for identity, name, type reference presence, root semantics, and local lifecycle preconditions. Do not introduce Spring/JPA/HTTP dependencies.
2. **M2-02 — PostgreSQL persistence and hierarchy queries.** Add a new Flyway migration, JPA mapping/adapter, normalized sibling uniqueness, child/type-usage queries, recursive cycle query, and PostgreSQL integration tests. Keep `ddl-auto: validate`; test self-parent, uniqueness, and real reload behavior.
3. **M2-03 — Application use cases and transactional tree rules.** Implement create, list/detail, update name, move, activate/deactivate. Coordinate type usage/parent/children checks, serializable conflict behavior, version handling, and semantic errors through ports. No cascade behavior.
4. **M2-04 — REST contract.** Add request/response DTOs, controller mappings, conditional version semantics, safe error mappings, and HTTP contract tests. Do not expose JPA entities.
5. **M2-05 — Angular + PO UI administration.** Implement list/detail and lazy hierarchy, create root/child, edit name, move via selector, and explicit lifecycle actions with clear server conflict feedback.
6. **M2-06 — Vertical acceptance.** Exercise actual HTTP, application, domain, persistence, and PostgreSQL behavior for roots/children, movement, cycles, activation/deactivation constraints, and frontend/backend contract. Keep broader Item acceptance outside M2.

## 19. Risks and implementation gates

- The canonical Unicode key algorithm is frozen above. M2-02 must test Java key generation and persisted-key uniqueness against PostgreSQL, then select a root-aware index/constraint strategy; application-only uniqueness is race-prone.
- Recursive cycle checks must be protected against concurrent topology changes; a row version alone is insufficient.
- AddressType deactivation currently has no usage check in M1 because Address does not exist. M2 must add a coordinated application rule without silently changing or cascading existing Address state.
- Item occupancy is frozen for future integration but cannot be enforced before Item exists. The future Item milestone must add it before exposing Address deactivation as fully safe with stored inventory.
- Serializable transactions can abort under contention. M2 must map/retry safely and test that retries cannot duplicate side effects; if the implementation cannot meet that contract, return for a design decision before weakening cycle safety.
- No tenant or actor audit model exists; avoid designing these by assumption.

M2 implementation is not authorized by this design document alone. Each implementation task requires its own scope and verification gate.
