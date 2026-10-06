package dev.akume.storage.catalog.domain.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/** The accepted display spelling and canonical uniqueness key for a category name. */
public final class ItemCategoryName {

    private final String displayName;
    private final String canonicalKey;

    private ItemCategoryName(String displayName) {
        this.displayName = displayName;
        this.canonicalKey = canonicalize(displayName);
    }

    /** Accepts a user-supplied name, stripping outer whitespace and rejecting blank values. */
    public static ItemCategoryName from(String rawName) {
        if (rawName == null) {
            throw new IllegalArgumentException("name must not be null");
        }

        String displayName = Normalizer.normalize(rawName.strip(), Normalizer.Form.NFC);
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return new ItemCategoryName(displayName);
    }

    /** Rebuilds a persisted display name, rejecting values that were not stored in accepted form. */
    public static ItemCategoryName reconstitute(String persistedName) {
        ItemCategoryName accepted = from(persistedName);
        if (!accepted.displayName.equals(persistedName)) {
            throw new IllegalArgumentException("persisted name must already be stripped");
        }
        return accepted;
    }

    public String displayName() {
        return displayName;
    }

    /** Canonical key for persistence-level global uniqueness enforcement. */
    public String canonicalKey() {
        return canonicalKey;
    }

    private static String canonicalize(String displayName) {
        return Normalizer.normalize(displayName.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemCategoryName that)) {
            return false;
        }
        return displayName.equals(that.displayName) && canonicalKey.equals(that.canonicalKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayName, canonicalKey);
    }
}
