package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.application.model.ItemReadView;

/** Creates an Item in an active category. */
public interface CreateItemUseCase {

    ItemReadView create(CreateItemCommand command);
}
