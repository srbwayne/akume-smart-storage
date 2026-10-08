package dev.akume.storage.catalog.application.port.out;

import dev.akume.storage.catalog.application.model.ItemReadView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Joined read projections for Item responses; never used to authorize mutations. */
public interface ItemReadProjectionRepository {

    Optional<ItemReadView> findById(UUID id);

    List<ItemReadView> findAll();
}
