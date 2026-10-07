package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.domain.model.ItemCategory;

/** Creates an ItemCategory. */
public interface CreateItemCategoryUseCase {

    ItemCategory create(CreateItemCategoryCommand command);
}
