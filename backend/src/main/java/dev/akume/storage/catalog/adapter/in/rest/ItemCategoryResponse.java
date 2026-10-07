package dev.akume.storage.catalog.adapter.in.rest;

import dev.akume.storage.catalog.domain.model.ItemCategory;

import java.util.UUID;

/** Public HTTP representation of an ItemCategory. */
public record ItemCategoryResponse(UUID id, String name, boolean active, int version) {

    public static ItemCategoryResponse from(ItemCategory category) {
        return new ItemCategoryResponse(category.id(), category.name(), category.active(), category.version());
    }
}
