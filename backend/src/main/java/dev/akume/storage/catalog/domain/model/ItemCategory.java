package dev.akume.storage.catalog.domain.model;

import java.util.UUID;

/** A catalog classification that may be assigned to Items by a later capability. */
public final class ItemCategory {

    private final UUID id;
    private ItemCategoryName name;
    private boolean active;
    private final int version;

    private ItemCategory(UUID id, ItemCategoryName name, boolean active, int version) {
        this.id = requireId(id);
        this.name = requireName(name);
        this.active = active;
        this.version = requireVersion(version);
    }

    /** Creates an active category with a new UUID and initial persistence version zero. */
    public static ItemCategory create(String name) {
        return new ItemCategory(UUID.randomUUID(), ItemCategoryName.from(name), true, 0);
    }

    /** Rebuilds persisted category state without generating or repairing persisted values. */
    public static ItemCategory reconstitute(UUID id, String name, boolean active, int version) {
        return new ItemCategory(id, ItemCategoryName.reconstitute(name), active, version);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name.displayName();
    }

    /** Canonical key for global uniqueness enforcement by a later persistence slice. */
    public String canonicalNameKey() {
        return name.canonicalKey();
    }

    public boolean active() {
        return active;
    }

    /** Persistence/application-managed optimistic version, represented without framework types. */
    public int version() {
        return version;
    }

    /** Replaces the mutable display classification while preserving identity and lifecycle. */
    public void rename(String newName) {
        name = ItemCategoryName.from(newName);
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

    private static ItemCategoryName requireName(ItemCategoryName name) {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        return name;
    }

    private static int requireVersion(int version) {
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        return version;
    }
}
