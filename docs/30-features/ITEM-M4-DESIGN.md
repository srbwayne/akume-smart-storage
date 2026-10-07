# M4 — Item Design

**Status: PROPOSED / AWAITING INDEPENDENT REVIEW**

This document proposes the Item domain and delivery contract for M4. It is a
design artifact only. It does not authorize implementation, migration, API, or
frontend work.

## 1. Purpose and decision labels

This proposal distinguishes:

- **Existing authority**: accepted repository governance, ADRs, product/domain
  documents, and the accepted ItemCategory M3 contract.
- **Existing convention**: behavior found in the current implementation. It
  informs consistency but does not itself require that M4 copy it.
- **Proposed M4 decision**: a design choice for independent review. It is not
  frozen until accepted.

The proposal is based on `main` at
`86c94aed3c97a25443817a2ae5c6eea52452193e`.

## 2. Existing authority and conventions

### 2.1 Existing authority

- The system is a modular monolith using DDD and hexagonal architecture
  (ADR-0001, ADR-0002, `docs/ARCHITECTURE.md`). Item belongs to the catalog
  capability, and infrastructure dependencies remain outside domain code.
- PostgreSQL is the V1 relational database. Flyway owns schema changes;
  Hibernate validates mappings and does not create or update schema
  (ADR-0003, `AGENTS.md`).
- Physical inventory state changes only when an Operation is executed
  (ADR-0004, `AGENTS.md`, `docs/DOMAIN.md`). Item CRUD must not become an
  alternate inventory mutation route.
- Internal UUID identity and a possible human-readable code are distinct
  concepts. Mutable descriptive information must not be encoded in permanent
  identity (`AGENTS.md`, `docs/DOMAIN.md`).
- The accepted M3 ItemCategory contract defines categories as configurable
  catalog data with UUID identity, mutable display name, active lifecycle,
  optimistic version, global canonical name uniqueness, and no hard delete.
- M3 freezes these Item-facing rules: active categories are eligible for new
  assignment; inactive categories cannot receive new assignments; existing
  references remain valid after deactivation; deactivation does not cascade;
  inactive categories remain resolvable; category rename preserves UUID
  association and does not rewrite Item identity.
- The V1 milestone plan separates M4 Items from M5 Inventory, M6 Operation
  foundation, and later operation workflows. These boundaries remain intact.

### 2.2 Existing conventions (observations, not new requirements)

- `ItemCategory` in `catalog` uses an immutable UUID, a domain-owned normalized
  name value, `active`, and a persistence-managed integer version. Its JPA
  entity is separate from the domain type. M3 REST exposes explicit DTOs and
  stable semantic errors.
- Address mutations carry an expected version and return server-authoritative
  results. Address tree operations use PostgreSQL `SERIALIZABLE` transactions
  with a bounded three-attempt retry boundary because they protect cross-row
  hierarchy predicates. This is a precedent for a specific M2 problem, not a
  requirement that every M4 mutation use serializable isolation.
- Existing REST adapters use `/api/...`, UUID path identity, DTOs separate from
  persistence entities, `201 Created` plus `Location` for creation, and an
  error envelope containing `status`, `code`, `message`, and `path`.
- Angular features use capability-oriented folders, explicit routes, and PO UI
  forms/tables. Address and ItemCategory frontends consume API-returned state.

### 2.3 Decision record — approved M4 product decisions

Product approval has accepted the following decisions for this proposal:

- Item is a catalog definition, not an individually owned physical unit.
  Physical quantities and individually tracked units are future
  Inventory/Operation concerns.
- Item identity is an immutable UUID. A human-readable Item code is not part
  of M4.
- Item requires `id`, `name`, `itemCategoryId`, `active`, and `version`;
  `description` is optional.
- Name is required, nonblank after `strip()`, and stored as the stripped
  display value. Item names are not globally unique.
- Description is optional, stripped, and blank becomes null. Preserve
  internal text and impose no arbitrary length limit.
- Category UUID is required. Do not store an authoritative category name on
  Item. New assignment requires an active category; existing references
  remain valid after deactivation; category rename preserves the association.
- Category reassignment is an explicit use case and requires Item
  `expectedVersion`.
- PostgreSQL category-row locking (`SELECT FOR UPDATE` or equivalent
  pessimistic write lock) in the same transaction as Item creation or
  reassignment is the approved coordination approach. Read `active` after
  lock acquisition. Item optimistic versioning separately protects stale
  Item changes. There is no general `SERIALIZABLE` retry policy and no
  automatic retry for semantic conflicts.
- Item creation starts active at version zero. Item activation/deactivation
  are explicit, idempotent, and independent of category active state. Check
  expected version before lifecycle no-op detection; a no-op does not
  increment version.
- The initial Item list includes active and inactive Items without filtering,
  search, sorting, or pagination contract.
- The REST response includes `id`, `name`, `description`, `active`, `version`,
  and a category summary (`id`, current `name`, `active`). The summary is a
  read projection.
- Item mutations never directly change quantity, stock balance, physical
  address, inventory movement, Operation state, or Operation history.

This record incorporates the independent architectural review correction:
Item activation and deactivation do not create or change a category
assignment, so `ITEM_CATEGORY_INACTIVE` is not an Item lifecycle error. The
whole design remains proposed and awaits independent review; this decision
record does not authorize implementation.

## 3. Proposed aggregate and identity

### 3.1 Aggregate boundary

**Proposed M4 decision:** introduce `Item` as an aggregate root in the existing
`catalog` capability. One Item has its own UUID identity and a reference to one
ItemCategory UUID. The aggregate does not contain an ItemCategory aggregate or
an inventory collection. Category eligibility is coordinated by the
application transaction through a catalog output port; Item does not own or
mutate category state.

Item is a catalog definition (for example, “ESP32-S3 development board”), not
an individually owned physical unit, a count, a current stock balance, or a
physical placement. Product approval fixes this meaning for M4. Physical
quantities and individually tracked units are future Inventory/Operation
concerns.

### 3.2 Internal and human-readable identity

- **UUID**: required, generated on creation, immutable, and used by API paths,
  references, and database primary key.
- **Human-readable item code**: defer from M4. No accepted workflow requires
  one, and code allocation introduces uniqueness, presentation, and lifecycle
  decisions. If needed later, add it as a separate stable field without
  replacing UUID identity.
- **Name**: descriptive and mutable; never an identifier.

### 3.3 Proposed fields

| Field | Purpose | Required / mutability | Validation and normalization | Persistence / API impact |
|---|---|---|---|---|
| `id: UUID` | Stable internal Item identity | Required; immutable; generated at create | Must not be null when reconstituted | UUID primary key; returned in every Item response and used in paths |
| `name: String` | Human-readable catalog name | Required; mutable through metadata update | Reject null/blank after `strip()` and persist the stripped value. Preserve internal whitespace and punctuation. Names are not globally unique | `TEXT NOT NULL`; returned in API and used in list/detail |
| `description: String?` | Optional context beyond the short name | Optional; mutable; null means no description | Apply `strip()`; convert blank to null; preserve internal text. No arbitrary length limit | Nullable `TEXT`; request may omit or send null; response includes the optional value |
| `itemCategoryId: UUID` | Classification association | Required; mutable only through explicit category reassignment | Must identify an existing category. New assignment requires active state after acquiring the category row lock. Existing inactive-category references remain valid | `UUID NOT NULL` restrictive FK to `item_categories(id)`; API uses UUID, never copies category name as authority |
| `active: boolean` | Item lifecycle state | Required; starts true; changed only by explicit lifecycle operations | Item lifecycle is independent of category active state; inactive does not mean out of inventory | `BOOLEAN NOT NULL`; returned by API; lifecycle mutation has expected-version contract |
| `version: integer` | Protects stale Item mutations | Required; initially zero; provider-managed after persistence | Non-negative; clients must submit observed value for mutation. Validate before lifecycle no-op detection | `INTEGER NOT NULL`; optimistic version column; returned in responses |

No generic attributes map, dimensions, brand, model, serial number, barcode,
price, unit of measure, image, tags, timestamps, tenant, or audit fields are
proposed for M4. A typed attribute model needs concrete item workflows and
should not be smuggled in as JSON metadata.

## 4. ItemCategory association and eligibility

**Proposed M4 decision:** persist only `item_category_id` as the association.
Do not store the category name as authoritative Item data. Resolve the
category when reading an Item view so a category rename is reflected without
changing the Item UUID or rewriting Item state.

Rules inherited from the accepted M3 contract and approved for M4:

1. Creating an Item requires an existing active ItemCategory.
2. Reassigning an Item to a category requires the target category to exist and
   be active at the assignment transaction’s decision point.
3. Item activation and deactivation do not assign or reassign a category and
   remain allowed when the Item's existing category is inactive. These
   lifecycle commands do not perform a category eligibility check.
4. Deactivating a category is allowed even when Items reference it. It does
   not deactivate Items, clear references, or mutate inventory.
5. Existing references to inactive categories remain valid and resolvable.
   Detail/list responses should include current category identity, display
   name, and active status (or a documented summary with those facts).
6. Reactivation makes the category eligible for future assignments again.
7. Category deletion is not part of M3 or M4. A restrictive FK prevents
   accidental deletion while Items reference a category.

The Item domain object may hold the category UUID, but it must not import
Spring, JPA, HTTP, or the ItemCategory persistence adapter. Cross-aggregate
eligibility and locking belong to application coordination and an output port.

## 5. Concurrency and transaction design

### 5.1 Required decision point

The system must define “active when assigned” with a database-serialized
decision point. A plain read of `active=true` followed later by an Item write
is insufficient: deactivation could commit between those actions. Item’s own
optimistic version protects Item state only; it cannot serialize a category
row that it did not update.

The transaction must make the eligibility check and Item insert/reassignment
atomic. If deactivation serializes first, the assignment sees inactive and
is rejected. If assignment serializes first, it may commit the reference and
the subsequent deactivation may still succeed; that pre-existing reference
then remains valid by the accepted M3 contract.

### 5.2 Viable approach A — lock the category row (recommended)

Within the same PostgreSQL transaction as Item creation/reassignment:

1. Acquire a write-conflicting row lock on the target `item_categories` row,
   using a repository/output-port operation backed by `SELECT ... FOR UPDATE`
   or an equivalent JPA pessimistic write lock.
2. After the lock is acquired, read the current category active state and
   version. Missing category becomes not found; inactive target becomes a
   stable semantic ineligible-category conflict.
3. Insert/update the Item and commit in the same transaction.
4. Category deactivation/activation already updates this same category row, so
   PostgreSQL row locking establishes an order between assignment and
   lifecycle transition. The category row lock is held until commit/rollback.

For reassignment of an existing Item, verify its expected version and protect
the update with its optimistic version. Lock the target category; do not lock
the source category when moving away from it. If a future use case must lock
multiple categories, acquire them in ascending UUID order. Category lifecycle
code must not lock Item rows or scan/mutate Items. This keeps lock acquisition
consistent and avoids a category-to-Item lock cycle.

**Benefits:** direct linearization around the exact active flag; works at
PostgreSQL `READ COMMITTED`; bounded waiting without general serializable
predicate retries; straightforward acceptance tests. **Costs:** concurrent
assignments to the same category serialize briefly; lock timeout/deadlock
handling must map safely and must not fabricate success. Keep transactions
short and perform no external I/O while holding the row lock.

### 5.3 Alternative considered — serializable transaction

An alternative is to run the eligibility read and Item write in one
`SERIALIZABLE` PostgreSQL transaction. This is not the approved M4 strategy.
The accepted M2 bounded retry policy solves M2 hierarchy predicates and is
not generalized to Item assignment. No general serializable retry policy is
proposed or approved here. A future need for serializable handling would
require a separate design decision, whole-transaction replay analysis, and
proof against duplicate side effects.

**Potential benefit:** can protect more complex cross-row predicates if a
future feature establishes that requirement. **Costs:** increased abort and
transaction complexity, and an unclear linearization contract for the simple
single-category active check. Serializable isolation must not be assumed to
produce the desired assignment/deactivation order without validating the
exact PostgreSQL read/write set. The category row lock makes that decision
point explicit with less machinery.

### 5.4 Recommendation and retry policy

Approach A is approved for this single-row eligibility invariant. Use an
ordinary transaction with the category row lock plus Item `@Version` for
stale Item mutations. Check expected version before lifecycle no-op handling.
Do not automatically retry semantic conflicts, stale Item conflicts, or
general serializable failures. A deadlock or lock timeout is an infrastructure
failure that must roll back and be reported safely; any retry policy requires
a separate explicit decision. The losing assignment in a normal category
race waits, then observes inactive and returns the semantic rejection.

### 5.5 Race outcomes to accept

**Creation versus deactivation** (existing category starts active):

- Item create locks/checks active first, then commits; category deactivation
  subsequently commits. Both commands succeed, and the Item references the
  now-inactive category. This is valid because the assignment linearized
  first and existing references survive deactivation.
- Category deactivation commits first; Item create then sees inactive and is
  rejected. Deactivation succeeds; no Item row is created.
- Unknown category: Item creation is rejected as not found; no Item row is
  created.
- Database/system failure: transaction rolls back; no partially created Item.

**Reassignment versus target-category deactivation:**

- Reassignment locks/checks target first and commits; target deactivation may
  then commit. Both succeed and the existing Item remains associated with the
  now-inactive target.
- Target deactivation commits first; reassignment sees inactive and is
  rejected. The Item retains its prior category, name, active state, and
  version.
- A stale Item version rejects reassignment without changing the Item even if
  the target category is active.
- Every other outcome, including successful assignment after observing an
  inactive target or partial state after failure, fails acceptance.

These race outcomes are proposals, not test evidence; concurrency behavior
must be demonstrated against real PostgreSQL in M4 acceptance.

## 6. Lifecycle and mutability

**Proposed M4 use cases:**

- Create Item.
- List Items (all active and inactive Items; no initial search, filter,
  pagination, or sorting contract).
- Get Item by UUID.
- Update descriptive metadata (name and description).
- Reassign ItemCategory explicitly.
- Activate Item.
- Deactivate Item.

Creation starts an Item active at version zero. Rename/metadata update does
not change category, lifecycle, UUID, or inventory. Category reassignment is
an explicit M4 use case and must enforce target eligibility using the
approved category-row lock. It requires expected Item version and returns the
authoritative updated Item snapshot.

Lifecycle actions are explicit and idempotent. They do not check whether the
Item's existing category is active because they do not create or change a
category assignment. Stale expected versions are rejected before no-op
handling. A successful state change uses provider versioning; a no-op does
not increment version. Deactivating an Item does not remove it from
Inventory, alter quantity/location, or cancel/mutate Operations. Whether
inactive Items may appear on future Operation drafts or be executed is a
cross-milestone decision for M6/M7, not an inventory effect in M4.

No hard delete is proposed. Deactivation preserves Item and category history
and avoids cascading loss of references. No Item lifecycle action changes
physical stock state.

## 7. Inventory and Operation boundary

Item is catalog metadata and identity. M4 may not directly mutate:

- quantity or stock balance;
- current physical address/location;
- inventory movement records;
- operation lines or execution history.

M5 defines how current inventory is represented and queried. M6 defines
Operation and OperationLine contracts. M7+ execution performs stock
transitions atomically with operation status and history. Creating/editing an
Item, changing its category, or activating/deactivating it must not call an
inventory write path or imply an ENTRY, EXIT, or INTERNAL_MOVEMENT.

## 8. Persistence proposal

**Proposed table:** `items`, introduced only by a new Flyway migration in the
authorized persistence slice. Never modify an accepted migration. Hibernate
remains `ddl-auto: validate`.

| Column | Proposed definition | Notes |
|---|---|---|
| `id` | `UUID NOT NULL PRIMARY KEY` | Internal Item identity |
| `name` | `TEXT NOT NULL` | Stripped display name |
| `description` | `TEXT NULL` | Optional descriptive text; blank becomes null |
| `item_category_id` | `UUID NOT NULL` | FK to `item_categories(id)` |
| `active` | `BOOLEAN NOT NULL` | Starts true; lifecycle only |
| `version` | `INTEGER NOT NULL CHECK (version >= 0)` | Optimistic Item update token |

Use a restrictive/no-action FK (`ON DELETE RESTRICT` or PostgreSQL's default
`NO ACTION` with equivalent behavior). M3 exposes no category deletion and M4
proposes no Item deletion. The FK provides referential integrity, not active
eligibility; active-state eligibility is enforced transactionally in the
application through the category row lock.

No unique Item name is proposed: distinct Items can share descriptive names,
and no accepted authority defines uniqueness. No code, slug, serial number,
canonical key, generic attributes JSON, or speculative metadata columns are
proposed. The primary key and FK index support identity/reference checks.
Whether to add a separate `item_category_id` index depends on measured list
or join plans; do not add it speculatively. There is no quantity, address,
inventory, Operation, or movement column in M4.

## 9. Application and REST proposal

Use focused catalog input ports and commands; do not add a generic CRUD base
service. Application transactions own orchestration and interact with an
Item repository plus an ItemCategory eligibility/locking output port. The
domain remains framework-independent. Every mutation requires
`expectedVersion` once the Item exists and returns the persisted authoritative
snapshot.

### 9.1 Candidate use cases

| Use case | Input | Main result / semantic errors |
|---|---|---|
| Create Item | name, optional description, itemCategoryId | Created Item; invalid input, category not found, inactive category |
| List Items | none | All active and inactive Item snapshots |
| Get Item | UUID | Item snapshot; Item not found |
| Update Item metadata | UUID, name, description, expectedVersion | Persisted snapshot; not found, invalid input, stale version |
| Reassign ItemCategory | UUID, itemCategoryId, expectedVersion | Persisted snapshot; Item/category not found, inactive target, stale version |
| Activate Item | UUID, expectedVersion | Persisted snapshot; not found, stale version |
| Deactivate Item | UUID, expectedVersion | Persisted snapshot; not found, stale version |

Category reassignment is a separate explicit use case so category eligibility
and its locking boundary are visible and independently testable.

### 9.2 Candidate REST endpoints

All endpoints are proposals, not implementation authorization.

| Method / path | Request fields | Success | Relevant errors |
|---|---|---|---|
| `POST /api/items` | `name`, optional `description`, `itemCategoryId` | `201 Created`, `Location: /api/items/{id}`, Item response | `400 INVALID_REQUEST`; `404 ITEM_CATEGORY_NOT_FOUND`; `409 ITEM_CATEGORY_INACTIVE` |
| `GET /api/items` | none | `200 OK`, all active/inactive Items | none specific |
| `GET /api/items/{id}` | UUID path | `200 OK`, Item response with current category summary | `404 ITEM_NOT_FOUND` |
| `PUT /api/items/{id}` | `name`, optional/null `description`, `expectedVersion` | `200 OK`, authoritative response | `400 INVALID_REQUEST`; `404 ITEM_NOT_FOUND`; `409 ITEM_CONCURRENT_MODIFICATION` |
| `PUT /api/items/{id}/category` | `itemCategoryId`, `expectedVersion` | `200 OK`, authoritative response | `400 INVALID_REQUEST`; `404 ITEM_NOT_FOUND` / `ITEM_CATEGORY_NOT_FOUND`; `409 ITEM_CATEGORY_INACTIVE` / `ITEM_CONCURRENT_MODIFICATION` |
| `POST /api/items/{id}/activate` | `expectedVersion` | `200 OK`, authoritative response | `400 INVALID_REQUEST`; `404 ITEM_NOT_FOUND`; `409 ITEM_CONCURRENT_MODIFICATION` |
| `POST /api/items/{id}/deactivate` | `expectedVersion` | `200 OK`, authoritative response | `400 INVALID_REQUEST`; `404 ITEM_NOT_FOUND`; `409 ITEM_CONCURRENT_MODIFICATION` |

`ITEM_CATEGORY_INACTIVE` applies only to `POST /api/items` and
`PUT /api/items/{id}/category`, which create or change a category assignment.
It does not apply to metadata update or Item lifecycle endpoints.

**Proposed Item response:** `id`, `name`, `description`, `active`, `version`,
and a small category summary containing `id`, current `name`, and `active`.
Do not expose JPA state, canonical category key, SQL/constraint details, or
inventory fields. The category summary is a read projection, not denormalized
authoritative state on Item.

Use the established four-field error envelope (`status`, `code`, `message`,
`path`) and safe messages. Unknown request fields and malformed UUID/body
should follow the strict M3 REST conventions. No delete, search, filter,
pagination, sorting, bulk, or inventory endpoints are proposed.

## 10. Angular / PO UI scope

**Proposed mandatory M4 frontend workflow:**

- `/items` route under the existing application navigation conventions;
- list active and inactive Items with name, category name/status, and Item
  lifecycle status;
- loading, empty, list failure, and explicit refresh states;
- create form with name, optional description, and a category selector showing
  only active categories for new assignment;
- metadata edit and explicit category reassignment;
- explicit activate/deactivate actions;
- server-returned Item and category snapshots replace local state after
  mutation; no locally invented UUID/version/state;
- pending-operation feedback and duplicate-submit protection;
- safe distinct feedback for invalid input, missing Item/category, inactive
  category on create/reassignment, and stale Item version;
- Item activation/deactivation remain available when the existing category is
  inactive;
- after a stale conflict or mutation 404, allow explicit user refresh; no
  automatic retry or silent overwrite;
- existing Item views continue resolving and showing an inactive category.

Possible later/deferred UI work: barcode/serial capture, imports, bulk
actions, advanced filters/search/pagination, inventory balances/location,
operation creation/execution, dashboard metrics, image upload, and generic
attribute editors. M4 should not add these through speculative UI controls.

## 11. Proposed acceptance criteria

These criteria describe future evidence to collect; no M4 implementation or
test evidence exists at design time.

### Domain and application

1. UUID identity is immutable; name/description changes preserve it.
2. Name rejects null/blank after accepted trimming; description null/blank
   follows the accepted optional-text rule.
3. Required category UUID is persisted; Item does not store category display
   name as authoritative state.
4. Create/update/reassignment/lifecycle return server-authoritative versions.
   A stale expected version fails before lifecycle idempotency and changes no
   state.
5. Activate/deactivate are explicit and idempotent; a no-op does not persist
   or increment version. Item lifecycle succeeds regardless of the active
   state of its existing category, subject to Item existence and version.
6. Item deactivation/reactivation never changes category or inventory.
7. Item creation, list, get, metadata update, reassignment, and lifecycle do
   not create Operation records or mutate Inventory/Operation state.

### PostgreSQL and persistence

8. New Flyway migration creates the Item table, UUID PK, required fields,
   restrictive ItemCategory FK, nonnegative version check, and only justified
   indexes. Hibernate validates the migration-created schema.
9. An unknown category is rejected and no Item row persists.
10. An inactive target is rejected for create and reassignment, and no Item
    state is changed. `ITEM_CATEGORY_INACTIVE` is not returned by metadata or
    lifecycle operations.
11. Existing Item FK remains valid when the referenced category is
    deactivated. The Item remains queryable and resolves the inactive category
    by UUID.
12. Concurrent stale Item updates cannot overwrite committed state; the
    persistence-managed version advances only on a successful state-changing
    update.

### Real PostgreSQL concurrency races

13. **Create wins lock:** arrange Item-create and category-deactivate to
    contend on one active category row. If create acquires the lock first,
    create succeeds, then deactivation succeeds; persisted Item references
    the same category UUID, category is inactive, and neither operation
    partially writes.
14. **Deactivate wins lock:** if deactivation acquires/commits first, create
    waits, then receives `ITEM_CATEGORY_INACTIVE`; no Item row exists.
15. **Reassignment wins lock:** if reassignment acquires target lock first,
    reassignment and later deactivation may both succeed; Item references the
    target UUID and target is inactive after both finish.
16. **Deactivation wins reassignment race:** reassignment sees inactive,
    returns the semantic conflict, and persisted Item retains its previous
    category UUID, name, active state, and version.
17. Race tests assert gate overlap, both outcomes, final database rows and
    category state. No sleep-based ordering. Under the recommended row-lock
    scheme normal race terminal pairs are exactly assignment success plus
    deactivation success, or assignment semantic rejection plus deactivation
    success. Deadlock/connection/unknown errors are failures, not accepted
    domain outcomes. Any bounded infrastructure retry policy requires a
    separately approved contract and direct no-duplicate-side-effect proof.

### REST and frontend

18. REST tests verify create `201`/Location, list/detail `200`, mutation `200`,
    validation `400`, not found `404`, inactive-category conflicts only for
    create/reassignment, stale Item conflicts `409`, strict request DTO
    behavior, exact response fields, and safe error envelopes. Item
    activation/deactivation succeed for an Item whose existing category is
    inactive when the Item version is current.
19. PostgreSQL-backed REST tests exercise representative create/get, update,
    reassignment, lifecycle, inactive-category rejection, stale version, and
    category rename/deactivation resolution paths.
20. Component/service tests assert exact API paths/payloads, active category
    choices for assignment, visible inactive references, authoritative
    responses, no automatic retry, manual refresh after conflict/not-found,
    and safe error messages.
21. Browser end-to-end coverage, if adopted by the project, proves at least
    one real user flow through Angular and the API; component or MockMvc tests
    must not be described as browser E2E evidence.

## 12. Deferred scope and non-goals

M4 does not implement Inventory or Operation capabilities. Specifically
deferred: stock quantity, physical address/location, ENTRY/EXIT/movement,
operation history, labels/QR/PDF, recommendations, capacity, hierarchy,
tenant/workspace, audit history, authorization design, bulk/import/export,
hard deletion, and microservices.

The following remain future questions outside the approved M4 product
decisions: how M5 represents aggregate quantities and how later capabilities
represent individually tracked units; whether M6/M7 permit inactive Items in
Operation drafts or execution; whether future evidence justifies an Item
category index; and whether the project adopts browser end-to-end tooling.
They do not change the approved M4 decision that an Item is a catalog
definition and carries no stock quantity or physical-unit identity.

## 13. Proposed implementation slices

These slices are a dependency proposal only. None is authorized by this
document.

1. **M4-01 — Domain:** implement the approved catalog-definition Item
   identity, minimal fields, category reference semantics, lifecycle,
   validation, and domain tests. No Spring/JPA dependencies.
2. **M4-02 — Persistence:** new Flyway migration, JPA mapping, restrictive
   category FK, Item optimistic locking, and PostgreSQL tests. Add the
   category row-lock adapter/output-port method and real transaction tests
   for the approved assignment/deactivation coordination strategy.
3. **M4-03 — Application:** focused input ports/use cases, transactional
   category eligibility, metadata/reassignment/lifecycle orchestration, and
   application tests. Depends on M4-01 and M4-02.
4. **M4-04 — REST:** request/response DTOs, endpoints, stable errors, focused
   controller tests, and vertical PostgreSQL REST acceptance. Depends on the
   frozen application contract.
5. **M4-05 — Frontend:** Item administration route, category selector,
   metadata/lifecycle workflow, stale-state recovery, PO UI tests. Depends on
   published REST contract.
6. **M4-06 — Acceptance:** cross-layer Item/category scenarios, concurrency
   races against PostgreSQL, inventory isolation, full test suites and builds.
   Does not itself add Inventory or Operation features.

Each slice requires its own explicit gate, source review, and checkpoint
authorization. The sequence above grants none of those permissions.

## 14. Remaining future-milestone questions

These questions do not reopen the approved M4 product decisions recorded in
Section 2.3. Resolve them in the named future design slice before that scope
is implemented:

1. How should M5 represent quantities for catalog definitions, and what later
   model is needed for individually tracked physical units?
2. May inactive Items appear in M6 Operation drafts or be executed in M7?
3. Should measured M4/M5 query plans justify an index on
   `item_category_id`?
4. Will the project use browser end-to-end tooling for V1 acceptance, or rely
   on component/service plus backend vertical acceptance tests?
