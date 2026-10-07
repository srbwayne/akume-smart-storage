package dev.akume.storage.catalog.application.port.in;

import dev.akume.storage.catalog.domain.model.ItemCategory;

import java.util.List;

/** Lists every ItemCategory, including inactive categories. */
public interface ListItemCategoriesUseCase {

    List<ItemCategory> listAll();
}
