package dev.akume.storage.catalog.application.exception;

import java.util.UUID;

/** Indicates that the requested ItemCategory does not exist. */
public class ItemCategoryNotFoundException extends RuntimeException {

    private final UUID itemCategoryId;

    public ItemCategoryNotFoundException(UUID itemCategoryId) {
        super("Item category was not found: " + itemCategoryId);
        this.itemCategoryId = itemCategoryId;
    }

    public UUID itemCategoryId() {
        return itemCategoryId;
    }
}
