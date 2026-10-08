package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.domain.model.Item;

/** Activates an Item without changing its category assignment. */
public interface ActivateItemUseCase {

    Item activate(ActivateItemCommand command);
}
