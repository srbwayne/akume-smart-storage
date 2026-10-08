package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.domain.model.Item;

import java.util.UUID;

/** Retrieves an Item by UUID. */
public interface GetItemUseCase {

    Item getById(UUID id);
}
