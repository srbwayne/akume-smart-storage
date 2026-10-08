package dev.akume.storage.catalog.application.model;

import java.util.Objects;
import java.util.UUID;

/** Application read projection for an Item and the current state of its category. */
public record ItemReadView(
        UUID id,
        String name,
        String description,
        UUID itemCategoryId,
        boolean active,
        int version,
        CategorySummary category) {

    public ItemReadView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(itemCategoryId, "itemCategoryId");
        Objects.requireNonNull(category, "category");
        if (!itemCategoryId.equals(category.id())) {
            throw new IllegalArgumentException("category id must match itemCategoryId");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    public record CategorySummary(UUID id, String name, boolean active) {
        public CategorySummary {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
        }
    }
}
