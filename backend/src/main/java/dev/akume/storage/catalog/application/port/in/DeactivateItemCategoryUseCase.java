package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.domain.model.ItemCategory;

/** Deactivates an ItemCategory without inspecting future Item references. */
public interface DeactivateItemCategoryUseCase {

    ItemCategory deactivate(DeactivateItemCategoryCommand command);
}
