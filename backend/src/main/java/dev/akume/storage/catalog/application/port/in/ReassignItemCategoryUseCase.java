package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.domain.model.Item;

/** Reassigns an Item to an active category using optimistic concurrency. */
public interface ReassignItemCategoryUseCase {

    Item reassignCategory(ReassignItemCategoryCommand command);
}
