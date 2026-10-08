package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.domain.model.Item;

/** Updates an Item's descriptive metadata using optimistic concurrency. */
public interface UpdateItemMetadataUseCase {

    Item updateMetadata(UpdateItemMetadataCommand command);
}
