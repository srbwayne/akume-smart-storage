package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.domain.model.Item;

import java.util.List;

/** Lists every Item, including inactive Items. */
public interface ListItemsUseCase {

    List<Item> listAll();
}
