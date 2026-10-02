package dev.akume.storage.location.domain.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

/**
 * One concrete physical address. Hierarchy-wide rules are coordinated outside this aggregate.
 */
public final class Address {

    private final UUID id;
    private String name;
    private String normalizedNameKey;
    private final UUID addressTypeId;
    private UUID parentId;
    private boolean active;
    private final int version;

    private Address(
            UUID id,
            String name,
            String normalizedNameKey,
            UUID addressTypeId,
            UUID parentId,
            boolean active,
            int version) {
        this.id = requireId(id);
        this.name = requireAcceptedDisplayName(name);
        this.normalizedNameKey = requireConsistentKey(this.name, normalizedNameKey);
        this.addressTypeId = requireAddressTypeId(addressTypeId);
        this.parentId = requireNonSelfParent(this.id, parentId);
        this.active = active;
        this.version = requireVersion(version);
    }

    /** Creates a new active address and derives its canonical sibling-name key. */
    public static Address create(String name, UUID addressTypeId, UUID parentId) {
        String displayName = requireDisplayName(name);
        UUID id = UUID.randomUUID();
        UUID validParentId = requireNonSelfParent(id, parentId);
        return new Address(
                id,
                displayName,
                normalizedNameKey(displayName),
                addressTypeId,
                validParentId,
                true,
                0);
    }

    /**
     * Rebuilds an address from persisted state without generating or repairing persisted values.
     * The persisted display name and canonical key must already agree with the domain policy.
     */
    public static Address reconstitute(
            UUID id,
            String name,
            String normalizedNameKey,
            UUID addressTypeId,
            UUID parentId,
            boolean active,
            int version) {
        return new Address(id, name, normalizedNameKey, addressTypeId, parentId, active, version);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String normalizedNameKey() {
        return normalizedNameKey;
    }

    public UUID addressTypeId() {
        return addressTypeId;
    }

    /** A null parent identifies a root address. */
    public UUID parentId() {
        return parentId;
    }

    public boolean active() {
        return active;
    }

    /** Infrastructure-managed optimistic version, represented without framework types. */
    public int version() {
        return version;
    }

    /** Updates the editable detail and its canonical key as one domain operation. */
    public void updateDetails(String name) {
        String displayName = requireDisplayName(name);
        String key = normalizedNameKey(displayName);
        this.name = displayName;
        this.normalizedNameKey = key;
    }

    /**
     * Applies a parent change already authorized by the application layer.
     * This method enforces only the local self-parent invariant; it does not validate existence,
     * descendants, cycles, activity, or sibling-name uniqueness.
     */
    public void applyAuthorizedParentChange(UUID newParentId) {
        this.parentId = requireNonSelfParent(id, newParentId);
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

    private static UUID requireAddressTypeId(UUID addressTypeId) {
        if (addressTypeId == null) {
            throw new IllegalArgumentException("addressTypeId must not be null");
        }
        return addressTypeId;
    }

    private static UUID requireNonSelfParent(UUID id, UUID parentId) {
        if (id.equals(parentId)) {
            throw new IllegalArgumentException("parentId must not equal id");
        }
        return parentId;
    }

    private static int requireVersion(int version) {
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        return version;
    }

    private static String requireAcceptedDisplayName(String name) {
        String displayName = requireDisplayName(name);
        if (!displayName.equals(name)) {
            throw new IllegalArgumentException("persisted name must already be stripped");
        }
        return name;
    }

    private static String requireDisplayName(String rawName) {
        if (rawName == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        String displayName = rawName.strip();
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return displayName;
    }

    private static String requireConsistentKey(String name, String normalizedNameKey) {
        String expectedKey = normalizedNameKey(name);
        if (!expectedKey.equals(normalizedNameKey)) {
            throw new IllegalArgumentException("normalizedNameKey does not match name");
        }
        return normalizedNameKey;
    }

    private static String normalizedNameKey(String rawName) {
        String strippedName = rawName.strip();
        String firstNfc = Normalizer.normalize(strippedName, Normalizer.Form.NFC);
        String lowerCase = firstNfc.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lowerCase, Normalizer.Form.NFC);
    }
}
