package dev.akume.storage.catalog.adapter.in.rest;

import dev.akume.storage.catalog.application.model.ItemReadView;

import java.util.UUID;

/** Public HTTP representation of an Item. */
public record ItemResponse(UUID id, String name, String description, UUID itemCategoryId,
                           boolean active, int version, ItemCategorySummaryResponse category) {

    public static ItemResponse from(ItemReadView item) {
        return new ItemResponse(item.id(), item.name(), item.description(), item.itemCategoryId(),
                item.active(), item.version(), ItemCategorySummaryResponse.from(item.category()));
    }
}
