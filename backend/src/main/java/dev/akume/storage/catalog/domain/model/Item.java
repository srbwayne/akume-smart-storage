package dev.akume.storage.catalog.domain.model;

import java.util.UUID;

/** A catalog definition classified by an ItemCategory, independent of physical inventory. */
public final class Item {

    private final UUID id;
    private String name;
    private String description;
    private UUID itemCategoryId;
    private boolean active;
    private final int version;

    private Item(
            UUID id,
            String name,
            String description,
            UUID itemCategoryId,
            boolean active,
            int version) {
        this.id = requireId(id);
        this.name = normalizeName(name);
        this.description = normalizeDescription(description);
        this.itemCategoryId = requireItemCategoryId(itemCategoryId);
        this.active = active;
        this.version = requireVersion(version);
    }

    /** Creates an active catalog definition with a new UUID and initial persistence version zero. */
    public static Item create(String name, String description, UUID itemCategoryId) {
        return new Item(UUID.randomUUID(), name, description, itemCategoryId, true, 0);
    }

    /** Rebuilds an existing Item from persisted state without generating a new identity. */
    public static Item reconstitute(
            UUID id,
            String name,
            String description,
            UUID itemCategoryId,
            boolean active,
            int version) {
        return new Item(id, name, description, itemCategoryId, active, version);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public UUID itemCategoryId() {
        return itemCategoryId;
    }

    public boolean active() {
        return active;
    }

    /** Persistence-managed optimistic version, represented without framework types. */
    public int version() {
        return version;
    }

    /** Updates descriptive metadata while preserving identity, category, lifecycle, and version. */
    public void updateDetails(String name, String description) {
        String acceptedName = normalizeName(name);
        String acceptedDescription = normalizeDescription(description);
        this.name = acceptedName;
        this.description = acceptedDescription;
    }

    /** Changes only the category reference; category eligibility is checked outside the domain. */
    public void reassignCategory(UUID itemCategoryId) {
        this.itemCategoryId = requireItemCategoryId(itemCategoryId);
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    private static UUID requireId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        return id;
    }

    private static UUID requireItemCategoryId(UUID itemCategoryId) {
        if (itemCategoryId == null) {
            throw new IllegalArgumentException("itemCategoryId must not be null");
        }
        return itemCategoryId;
    }

    private static int requireVersion(int version) {
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        return version;
    }

    private static String normalizeName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        String normalized = name.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return normalized;
    }

    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
