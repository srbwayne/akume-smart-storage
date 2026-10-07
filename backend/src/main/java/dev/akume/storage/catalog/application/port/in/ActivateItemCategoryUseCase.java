package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.domain.model.ItemCategory;

/** Activates an ItemCategory. */
public interface ActivateItemCategoryUseCase {

    ItemCategory activate(ActivateItemCategoryCommand command);
}
