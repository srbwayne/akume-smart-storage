package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.application.model.ItemReadView;

import java.util.UUID;

/** Retrieves an Item by UUID. */
public interface GetItemUseCase {

    ItemReadView getById(UUID id);
}
