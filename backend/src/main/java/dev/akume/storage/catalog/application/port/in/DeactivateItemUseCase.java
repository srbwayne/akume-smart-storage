package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.application.model.ItemReadView;

/** Deactivates an Item without changing its category assignment. */
public interface DeactivateItemUseCase {

    ItemReadView deactivate(DeactivateItemCommand command);
}
