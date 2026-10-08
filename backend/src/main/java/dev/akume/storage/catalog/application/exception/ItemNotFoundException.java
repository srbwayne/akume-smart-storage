package dev.akume.storage.catalog.application.exception;

import java.util.UUID;

/** Indicates that the requested Item does not exist. */
public class ItemNotFoundException extends RuntimeException {

    private final UUID itemId;

    public ItemNotFoundException(UUID itemId) {
        super("Item was not found: " + itemId);
        this.itemId = itemId;
    }

    public UUID itemId() {
        return itemId;
    }
}
