package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.domain.model.ItemCategory;

import java.util.UUID;

/** Retrieves one ItemCategory by UUID. */
public interface GetItemCategoryUseCase {

    ItemCategory getById(UUID id);
}
