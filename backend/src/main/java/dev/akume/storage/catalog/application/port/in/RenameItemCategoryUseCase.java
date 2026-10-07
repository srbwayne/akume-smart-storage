package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.domain.model.ItemCategory;

/** Renames an ItemCategory using optimistic concurrency. */
public interface RenameItemCategoryUseCase {

    ItemCategory rename(RenameItemCategoryCommand command);
}
