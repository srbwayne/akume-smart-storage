package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.application.model.ItemReadView;

/** Activates an Item without changing its category assignment. */
public interface ActivateItemUseCase {

    ItemReadView activate(ActivateItemCommand command);
}
