package dev.akume.storage.catalog.application.exception;

import java.util.UUID;

/** Indicates that an inactive category cannot receive a new Item assignment. */
public class InactiveItemCategoryException extends RuntimeException {

    private final UUID itemCategoryId;

    public InactiveItemCategoryException(UUID itemCategoryId) {
        super("Item category is inactive: " + itemCategoryId);
        this.itemCategoryId = itemCategoryId;
    }

    public UUID itemCategoryId() {
        return itemCategoryId;
    }
}
