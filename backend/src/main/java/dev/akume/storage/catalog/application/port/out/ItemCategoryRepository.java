package dev.akume.storage.catalog.application.port.out;

import dev.akume.storage.catalog.domain.model.ItemCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence operations required by ItemCategory creation, updates, and reads. */
public interface ItemCategoryRepository {

    ItemCategory insert(ItemCategory category);

    Optional<ItemCategory> update(ItemCategory category);

    Optional<ItemCategory> findById(UUID id);

    List<ItemCategory> findAll();
}
