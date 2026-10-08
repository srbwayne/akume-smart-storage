package dev.akume.storage.catalog.adapter.in.rest;

import dev.akume.storage.catalog.application.model.ItemReadView;

import java.util.UUID;

/** Current read projection of the category assigned to an Item. */
public record ItemCategorySummaryResponse(UUID id, String name, boolean active) {

    static ItemCategorySummaryResponse from(ItemReadView.CategorySummary category) {
        return new ItemCategorySummaryResponse(category.id(), category.name(), category.active());
    }
}
