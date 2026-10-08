package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.domain.model.Item;

/** Deactivates an Item without changing its category assignment. */
public interface DeactivateItemUseCase {

    Item deactivate(DeactivateItemCommand command);
}
