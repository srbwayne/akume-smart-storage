package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.application.model.ItemReadView;

/** Reassigns an Item to an active category using optimistic concurrency. */
public interface ReassignItemCategoryUseCase {

    ItemReadView reassignCategory(ReassignItemCategoryCommand command);
}
