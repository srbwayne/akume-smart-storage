package dev.akume.storage.location.domain.model;

import java.util.UUID;

/**
 * A configurable semantic classification for physical storage addresses.
 */
public final class AddressType {

    private final UUID id;
    private final String code;
    private String name;
    private String description;
    private boolean active;

    private AddressType(String code, String name, String description) {
        this(UUID.randomUUID(), code, name, description, true);
    }

    private AddressType(UUID id, String code, String name, String description, boolean active) {
        this.id = requireId(id);
        this.code = requireText(code, "code");
        this.name = requireText(name, "name");
        this.description = description;
        this.active = active;
    }

    private static UUID requireId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        return id;
    }

    /** Rebuilds an existing address type from its persisted state. */
    public static AddressType reconstitute(UUID id, String code, String name, String description, boolean active) {
        return new AddressType(id, code, name, description, active);
    }

    public static AddressType create(String code, String name, String description) {
        return new AddressType(code, name, description);
    }

    public UUID id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public boolean active() {
        return active;
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    public void updateDetails(String name, String description) {
        this.name = requireText(name, "name");
        this.description = description;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null or blank");
        }
        return value;
    }
}
