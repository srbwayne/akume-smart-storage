package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.domain.model.Item;

/** Creates an Item in an active category. */
public interface CreateItemUseCase {

    Item create(CreateItemCommand command);
}
