package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.application.model.ItemReadView;

/** Updates an Item's descriptive metadata using optimistic concurrency. */
public interface UpdateItemMetadataUseCase {

    ItemReadView updateMetadata(UpdateItemMetadataCommand command);
}
