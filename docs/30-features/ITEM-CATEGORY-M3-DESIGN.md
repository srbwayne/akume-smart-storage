# M3 — Item Category Design

**Status: PROPOSED / AWAITING INDEPENDENT REVIEW**

This document proposes the V1 Item Category contract. It is a design artifact,
not an implementation authorization. No Item Category code, API, frontend, or
database migration is created by this proposal.

## 1. Authority and decision labels

This document distinguishes three kinds of statements:

- **Existing authority** is established by accepted repository governance,
  ADRs, product/domain documents, or previously accepted milestone contracts.
- **Existing convention** is behavior found in the current implementation.
  It is evidence to consider, not automatically a requirement for M3.
- **Proposed M3 decision** is a new choice for review. It is not frozen until
  independently reviewed and accepted.

The proposal is based on the repository at `main` commit
`5e7e04390ae022ff76001be5446a82e76eb46fb8`.

## 2. Existing authority and conventions

### 2.1 Existing authority

- V1 includes configurable Item Categories for classifying Items; categories
  are data, not a closed enum (`docs/DOMAIN.md`, `docs/milestones/V1.md`).
- The product examples (cables, electronics, boards, adapters, power supplies,
  tools, and similar) are examples, not seed data or a fixed category list.
- Backend architecture is a modular monolith using DDD and hexagonal
  boundaries. Domain code must not depend on Spring, persistence, HTTP, or
  frontend concerns (`ADR-0001`, `ADR-0002`, `docs/ARCHITECTURE.md`).
- PostgreSQL is the V1 relational database. Flyway owns schema changes and
  Hibernate validates rather than creates or updates the schema (`ADR-0003`,
  `AGENTS.md`).
- Internal UUID identity and any future human-readable identifier are
  separate concepts. No mutable business information belongs in a permanent
  identifier (`AGENTS.md`, `docs/DOMAIN.md`).
- Physical inventory changes belong to Operation execution. Item Category
  must not introduce an alternate inventory mutation path (`ADR-0004`,
  `AGENTS.md`).
- No Item module or tenant/workspace concept exists at this baseline
  (`docs/30-features/ADDRESS-HIERARCHY-M2-DESIGN.md`).

### 2.2 Existing conventions inferred from code

- AddressType is a configurable domain entity currently located in the
  `location` module. It has UUID identity, immutable `code`, mutable `name`
  and `description`, and an `active` flag. Its current persistence table is
  `address_types`.
- AddressType's current database uniqueness is exact uniqueness of `code`.
  Its domain rejects null/blank text but preserves the supplied value; the
  current frontend trims values before sending them. AddressType currently
  has no version field or expected-version request contract.
- Address uses an integer optimistic version for mutations. M2's proposed
  contract requires snapshot-based expected versions and server-authoritative
  mutation responses. The accepted M2 implementation also uses `409` with a
  stable error envelope for conflicts.
- REST controllers use `/api/...`, UUID path identity, request/response DTOs
  separate from persistence entities, `201 Created` with a `Location` on
  create, and a compact error object with `status`, `code`, `message`, and
  `path`.
- Angular features are grouped by user capability. AddressType uses an
  explicit route, PO UI table/forms, explicit lifecycle actions, safe error
  feedback, and server-returned records to update visible state.
- Address's M2 design specifies trimmed display names and an NFC plus
  locale-independent lowercase key for its sibling-name uniqueness. That
  algorithm is a proposed Address contract and applies to sibling names; it
  is not already a global Item Category rule.

These implementation observations do not freeze M3 behavior by themselves.

## 3. Proposed M3 domain contract

### 3.1 Aggregate

**Proposed M3 decision:** `ItemCategory` is an aggregate root in a new
`catalog` backend capability. It has no child entities or category-owned
collection in M3. Each create, rename, activate, or deactivate command acts on
one category. This gives it an independent lifecycle and persistence identity
without introducing an aggregate containing the entire catalog.

Do not place it in `location` solely because AddressType currently resides
there: ItemCategory classifies Items and belongs to catalog. The proposed
package shape follows existing module boundaries:

```text
catalog/
├── domain/
├── application/
└── adapter/
```

Create only layers needed by a slice; the architecture guide explicitly
discourages empty abstractions.

### 3.2 Identity

**Proposed M3 decision:** use an internally generated UUID as the stable
primary/API identity. It is required and immutable. Do not add a `code`, slug,
or human-readable identifier in M3; current authority provides no such
requirement. The mutable category name is not identity. A later human-facing
code requires a separate reviewed decision and must remain distinct from UUID
and name.

### 3.3 Fields

**Proposed minimal V1 fields:**

| Field | Purpose | Required / mutability | Normalization | Persistence / REST |
|---|---|---|---|---|
| `id: UUID` | Stable internal identity | Required; immutable; generated on create | None | UUID primary key; returned in response and used in resource paths |
| `name: String` | User-facing classification label | Required; mutable through rename | Apply the proposed name policy below | Store display spelling and a canonical uniqueness key; expose display spelling |
| `active: boolean` | Controls whether a category may be newly selected for an Item | Required; starts `true`; changed only by explicit lifecycle actions | None | Required boolean; exposed in list/detail/mutation responses |
| `version: integer` | Optimistic concurrency token | Required; initialized to `0`; managed by persistence/application | Non-negative | Required version column; exposed in responses and required in mutation request bodies |

Do not add description, code, color, icon, parent, sort order, timestamps,
tenant, or audit metadata in M3 without a concrete user workflow and a new
decision. In particular, AddressType's `description` and immutable `code`
are not evidence that categories need those fields.

### 3.4 Name semantics

**Proposed M3 decision:** category names are unique across the entire
ItemCategory catalog, regardless of active state. Unlike Address, a category
has no parent-local sibling namespace.

- Trim outer whitespace using the Java `String.strip()` semantics already
  specified by the M2 Address design.
- Reject null or a value that is blank after trimming. Persist and display the
  trimmed spelling.
- Preserve internal whitespace and punctuation. Do not collapse spaces,
  transliterate, remove accents, or accent-fold.
- Compare names case-insensitively using this canonical key algorithm:

  ```java
  String displayName = input.strip();
  String normalizedNameKey = Normalizer.normalize(
      Normalizer.normalize(displayName, Normalizer.Form.NFC)
          .toLowerCase(Locale.ROOT),
      Normalizer.Form.NFC
  );
  ```

- Names that produce the same key conflict. The rule applies to active and
  inactive categories so deactivation cannot make a name available for a
  second category and reactivation cannot create a duplicate.
- Preserve accents and the project-defined Java lowercase behavior. For
  example, case-only differences and composed/decomposed canonical Unicode
  forms conflict; accent removal and broad Unicode full case-fold equivalence
  are not promised.

This borrows the M2 Address normalization primitive for consistent trimming
and key construction, but proposes a different uniqueness scope: global
catalog uniqueness rather than Address sibling uniqueness. It also differs
from current AddressType behavior, whose code—not name—is unique and whose
domain currently preserves input text. These are deliberate proposed choices,
not already frozen rules.

### 3.5 Lifecycle

**Proposed M3 decision:** categories have `ACTIVE` / `INACTIVE` lifecycle,
represented by the `active` field and changed only through explicit
`activate` and `deactivate` use cases/endpoints.

- New categories start active.
- Deactivation is non-cascading and does not delete the category.
- Reactivation is supported explicitly.
- There is no hard-delete use case or endpoint in M3.
- Deactivation does not free the category name.
- Idempotent activate/deactivate commands return the authoritative current
  category and do not increment the version when no state changes. This
  idempotency detail is a proposed M3 decision.

### 3.6 Item assignment semantics and deactivation while referenced

**Proposed M3 product semantics:** these Item-facing rules are defined now
to keep the M3 category contract compatible with M4. Their implementation is
not part of M3.

1. An inactive ItemCategory cannot be selected when creating a new Item.
2. An inactive ItemCategory cannot be newly assigned or reassigned to an
   existing Item.
3. Existing Items that already reference a category remain valid when that
   category is deactivated.
4. Reactivating a category makes it eligible for future Item assignment
   again.
5. Deactivation is not blocked by existing Item references, including
   references from active Items.
6. Deactivation does not cascade to Items, clear or rewrite an Item's
   `itemCategoryId`, deactivate Items, or delete inventory state.
7. Inactive categories remain queryable/resolvable. Existing Item references
   and historical/current Item views can therefore continue displaying the
   category, including its inactive status.
8. Deactivation means “unavailable for new assignment”; it does not erase
   the classification already recorded on an Item.

**Enforcement boundary:** M3 implements only ItemCategory. It must not
introduce an Item repository, port, usage check, Item mutation, or Item-side
validation. M4 implements the active-category check when an Item is created
or assigned/reassigned and must resolve existing references even when the
category is inactive. The exact M4 port/query and transaction coordination
are implementation decisions for M4; the product semantics above are not
deferred.

If Item assignment races with category deactivation in M4, the Item command
must validate category state transactionally. A new assignment may commit
only if its category is active at the assignment transaction's decision
point. A reference committed before deactivation remains valid after
deactivation. This race-coordination mechanism belongs to M4.

### 3.7 Rename semantics for future Item references

**Proposed M3 product semantics:** UUID is ItemCategory identity; the name is
a mutable display classification. Renaming never changes the category UUID.
When M4 Items reference a category through `itemCategoryId`, renaming does
not cascade or rewrite Item state. Resolving that reference after rename
returns the category's new display name. M3 does not add a denormalized
category-name copy to Item because Item is not implemented in M3.

### 3.8 Concurrency

**Proposed M3 decision:** use optimistic concurrency for mutable category
operations, following the Address mutation contract. This extends beyond the
current AddressType API, which has no version token; the reason is to prevent
stale category rename/lifecycle requests from silently overwriting a newer
state and to establish one safe contract for the new configurable catalog
resource.

- Creation returns version `0`.
- Rename and lifecycle requests include the version observed when the user
  began the operation.
- A successful state-changing mutation returns the complete authoritative
  category snapshot with a persistence-managed newer version. Clients must
  not guess or increment versions.
- A stale expected version changes no category state and returns `409` with
  `ITEM_CATEGORY_CONCURRENT_MODIFICATION`.
- Idempotent lifecycle commands that make no change return the current
  snapshot without a version increment.
- No automatic mutation retry or silent resubmission is allowed. A client may
  explicitly reload the current category and let the user retry.

Optimistic versioning protects updates to one category. It is not a substitute
for the unique-name database constraint. The database remains authoritative
for concurrent duplicate creates/renames.

### 3.9 Persistence

**Proposed M3 persistence concept; not final SQL or migration:**

Table: `item_categories`.

| Column | Proposed type / constraint | Meaning |
|---|---|---|
| `id` | `UUID PRIMARY KEY` | Internal identity |
| `name` | `TEXT NOT NULL` | Trimmed display name |
| `normalized_name_key` | `TEXT NOT NULL UNIQUE` | Application-generated canonical name key; exact database equality enforces global uniqueness |
| `active` | `BOOLEAN NOT NULL` | Explicit category lifecycle |
| `version` | `INTEGER NOT NULL CHECK (version >= 0)` | Optimistic concurrency token |

Use a named unique constraint for `normalized_name_key` so persistence can
translate only that conflict to the semantic name-conflict error. The unique
constraint is the only additional index justified by the M3 queries. The
primary key and unique constraint provide their indexes. Do not add an active
index, full-text index, category hierarchy columns, or Item foreign key before
query plans and the M4 integration justify them.

M3 has no Item foreign key. When M4 adds an Item reference, its migration must
use a restrictive foreign key so category history cannot be hard-deleted
through cascade. Future migrations must be new Flyway migrations; Hibernate
remains validation-only.

### 3.10 Application boundary

**Proposed minimal use cases:**

| Use case | Input | Output | Errors / concurrency |
|---|---|---|---|
| Create Item Category | `name` | Created category, version `0` | Blank/invalid name; `ITEM_CATEGORY_NAME_ALREADY_EXISTS` |
| List Item Categories | No input | All category snapshots, active and inactive | No mutation/version behavior |
| Get Item Category | UUID | Category snapshot | `ITEM_CATEGORY_NOT_FOUND` |
| Rename Item Category | UUID, name, expected version | Authoritative updated snapshot | Not found; invalid name; duplicate name; concurrent modification |
| Activate Item Category | UUID, expected version | Authoritative snapshot | Not found; concurrent modification |
| Deactivate Item Category | UUID, expected version | Authoritative snapshot | Not found; concurrent modification |

Do not add generic CRUD, bulk lifecycle, delete, category assignment, Item
queries, or an “in-use” check in M3. A list query can be implemented without
an output-port abstraction beyond what the selected persistence adapter
requires.

### 3.11 REST contract

**Proposed M3 API:** follow `/api/...`, UUID identity, DTO separation, stable
semantic errors, and explicit lifecycle actions.

Response shape:

```json
{
  "id": "<uuid>",
  "name": "Cable",
  "active": true,
  "version": 0
}
```

| Method and path | Request | Success | Relevant errors |
|---|---|---|---|
| `POST /api/item-categories` | `{ "name": "Cable" }` | `201 Created`, `Location: /api/item-categories/{id}`, response snapshot | `400 INVALID_REQUEST`; `409 ITEM_CATEGORY_NAME_ALREADY_EXISTS` |
| `GET /api/item-categories` | No query parameters or body | `200 OK`, list including active and inactive categories | No category-specific error |
| `GET /api/item-categories/{id}` | UUID path | `200 OK`, response snapshot | `404 ITEM_CATEGORY_NOT_FOUND` |
| `PUT /api/item-categories/{id}` | `{ "name": "Power", "expectedVersion": 0 }` | `200 OK`, authoritative response snapshot | `400 INVALID_REQUEST`; `404 ITEM_CATEGORY_NOT_FOUND`; `409 ITEM_CATEGORY_NAME_ALREADY_EXISTS`; `409 ITEM_CATEGORY_CONCURRENT_MODIFICATION` |
| `POST /api/item-categories/{id}/activate` | `{ "expectedVersion": 0 }` | `200 OK`, authoritative response snapshot | `400 INVALID_REQUEST`; `404 ITEM_CATEGORY_NOT_FOUND`; `409 ITEM_CATEGORY_CONCURRENT_MODIFICATION` |
| `POST /api/item-categories/{id}/deactivate` | `{ "expectedVersion": 0 }` | `200 OK`, authoritative response snapshot | `400 INVALID_REQUEST`; `404 ITEM_CATEGORY_NOT_FOUND`; `409 ITEM_CATEGORY_CONCURRENT_MODIFICATION` |

Use the established REST error envelope:

```json
{
  "status": 409,
  "code": "ITEM_CATEGORY_NAME_ALREADY_EXISTS",
  "message": "An item category with this name already exists.",
  "path": "/api/item-categories"
}
```

Messages must not expose SQL, constraint names, or stack traces. Do not
expose persistence entities. Do not add delete or bulk endpoints.

### 3.12 Queries and list semantics

**Proposed M3 decision:** support `list all` and `get by UUID`. The list
includes active and inactive records so the administration view can show and
reactivate all categories. M3 does not need an active filter: the first
administration workflow is small and must expose inactive categories.

No search, pagination, custom sorting, or generic query language is proposed
for the initial catalog size/workflow. Define stable ordering (for example,
display name then UUID) only if UI tests or product use establish that it is
needed; do not promise ordering accidentally through a database adapter.
M4 may later need an active-only selection query for Items, but that decision
belongs to M4 and does not add filtering to the M3 list endpoint.

### 3.13 Angular / PO UI contract

**Proposed M3 frontend behavior:** add a capability-oriented
`item-categories` feature and an explicit `/item-categories` route, consistent
with the existing `/address-types` route and the architecture's frontend
feature list.

- Show all categories with name and active/inactive status. Provide a clear
  empty state, loading state, and explicit retry for list failure.
- Provide create and rename forms for the name field only. Do not offer code,
  description, hierarchy, Item assignment, or deletion controls.
- Offer Activate for inactive records and Deactivate for active records.
  Keep lifecycle actions explicit; do not infer a toggle from a generic edit.
- Disable duplicate submissions for a pending category mutation, without
  globally locking unrelated navigation.
- Use server responses as authoritative state, including returned version.
- On `ITEM_CATEGORY_CONCURRENT_MODIFICATION`, do not auto-retry or pretend
  the mutation succeeded. Show safe conflict feedback and provide a
  user-controlled reload of the category/list before another attempt.
- Show a distinct safe name-conflict message; map only established category
  error codes and use generic safe feedback for unknown failures.
- Inactive status is visible, not a reason to hide or delete the record.

These are proposed frontend requirements for a later slice. No Angular code
is authorized by this design gate.

## 4. Canonical invariants

All invariants below are **proposed M3 decisions** until reviewed and
accepted. Persistence details are intentionally separated from domain rules.

### 4.1 Domain invariants

1. Every ItemCategory has one non-null UUID identity that does not change
   during its lifetime. The mutable name is not identity.
2. Every ItemCategory has a non-null name whose Java `strip()` result is not
   blank; the accepted display name is that stripped result.
3. Two ItemCategories cannot have equal canonical name keys, whether either
   category is active or inactive.
4. A newly created ItemCategory is active.
5. Lifecycle state changes only by explicit activate/deactivate behavior;
   lifecycle is not changed by rename.
6. Deactivation does not delete or mutate another domain object.
7. An idempotent activation/deactivation request leaves category state and
   version unchanged and returns the current state.
8. Renaming changes only the category's display name; the category UUID is
   unchanged.

### 4.2 Application / cross-aggregate invariants

1. A rename or lifecycle command with an expected version different from the
   current category version performs no state change and returns the
   concurrent-modification error.
2. Each successful state-changing rename/lifecycle command returns the full
   server-authoritative category snapshot and its persistence-managed version;
   clients do not calculate that version.
3. Concurrent create/rename operations that would produce the same
   canonical name key cannot both commit successfully.
4. M3 category lifecycle does not inspect or mutate Items. M4 must reject
   creation or assignment/reassignment of an Item to an inactive category.
5. Deactivation is never blocked by existing Item references. Existing Items
   remain valid and resolvable through their `itemCategoryId`; deactivation
   does not cascade, clear, or rewrite those references.
6. Reactivation makes the category eligible for new assignments again.
7. Renaming preserves the category UUID. Existing Item references remain
   associated through `itemCategoryId`, and resolving them returns the new
   category display name. M3 does not store a denormalized Item category name.
8. A failed category mutation does not fabricate a new name, lifecycle state,
   or version in an adapter/client.

### 4.3 Persistence constraints

1. `item_categories.id` is a non-null UUID primary key.
2. `name`, `normalized_name_key`, `active`, and `version` are non-null.
3. `normalized_name_key` has a unique constraint across all rows, including
   inactive categories.
4. `version` is non-negative and is the optimistic concurrency token.
5. M3 schema is introduced only through a new Flyway migration; Hibernate
   does not create/update it.
6. M3 has no Item foreign key because Item is outside this slice. Any future
   Item reference must be restrictive on delete, not cascading.

## 5. Deferred decisions

- Whether a future business need justifies a stable human-readable category
  code.
- Whether descriptions, colors, icons, or other display metadata are needed.
- Whether the list needs pagination, search, user-controlled sorting, or an
  active filter beyond its all-state default.
- Whether full Unicode case folding beyond Java's specified
  `toLowerCase(Locale.ROOT)` behavior is needed.
- Exact database collation/encoding details, which must not redefine the
  application-generated canonical key.
- Exact M4 Item port/repository/query mechanism and transaction coordination
  for enforcing the frozen active-category assignment rule.
- Additional M4 Item mutation workflows and transactional race tests; M4
  must test that an inactive category cannot receive a new assignment while
  existing references survive deactivation.
- Any audit history, actor attribution, timestamps, or authorization policy.
- Any future category merge, replacement, archival, or usage analytics.

## 6. Non-goals

M3 Item Category does not include:

- Item implementation or Item editing;
- Inventory state or quantity;
- Entry, Exit, or Internal Movement;
- QR/PDF labels;
- Smart Placement or storage recommendations;
- AI/ML categorization or automatic category inference;
- category hierarchy or parent/child categories;
- category deletion, merging, or bulk operations;
- microservices, event streaming, or distributed architecture;
- unrelated refactors or cleanup.

## 7. Proposed implementation slices

The following sequence is a proposal for future authorization, not an
authorization to begin any slice:

1. **M3-01 — Domain:** ItemCategory aggregate behavior, identity, name key,
   lifecycle, and domain tests. No Spring/JPA dependencies. This freezes the
   category's aggregate behavior first.
2. **M3-02 — Persistence:** new Flyway migration, JPA entity/adapter, unique
   name conflict translation, optimistic version representation, and
   PostgreSQL integration tests. This establishes race-safe canonical-name
   uniqueness and version storage before application orchestration.
3. **M3-03 — Application:** input ports/commands and use cases against the
   established domain and persistence ports, plus application tests. Keep
   category use separate from future Item behavior.
4. **M3-04 — REST:** DTOs, controller, stable error mappings, HTTP contract
   tests. Confirm request/version semantics before frontend work.
5. **M3-05 — Angular / PO UI:** service/model, routed administration page,
   list/create/rename/lifecycle/concurrency UX, component tests.
6. **M3-06 — Acceptance:** cross-workflow frontend/API regression, full test
   suites, production builds, and audit of the complete M3 behavior.

Keeping domain/application decisions independently testable before database
and HTTP adapters matches the accepted DDD/hexagonal direction. Each slice
should be separately audited; this design task authorizes none of them.

## 8. Review questions

Independent review should explicitly accept or revise:

1. Global, case-insensitive normalized-name uniqueness across active and
   inactive categories.
2. The minimal field set of UUID, name, active, and version, with no code or
   description.
3. The frozen Item assignment semantics: inactive categories cannot receive
   new assignments; existing references remain valid after deactivation.
4. Allowing deactivation while active Items reference the category, without
   cascade, reference rewrite, or loss of queryability.
5. Rename preserving UUID-based Item association and resolving to the new
   category display name.
6. Optimistic concurrency on category rename and lifecycle mutations despite
   the current AddressType API not yet exposing a version.
7. The proposed REST request/response/error contract and the future slice
   order.
