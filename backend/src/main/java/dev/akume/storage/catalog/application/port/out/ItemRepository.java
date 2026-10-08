package dev.akume.storage.catalog.application.port.out;

import dev.akume.storage.catalog.domain.model.Item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence operations required by Item creation, reads, and optimistic updates. */
public interface ItemRepository {

    Item insert(Item item);

    Optional<Item> update(Item item);

    Optional<Item> findById(UUID id);

    List<Item> findAll();
}
