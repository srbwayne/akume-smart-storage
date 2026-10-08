package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.application.model.ItemReadView;

import java.util.List;

/** Lists every Item, including inactive Items. */
public interface ListItemsUseCase {

    List<ItemReadView> listAll();
}
