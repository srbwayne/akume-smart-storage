package dev.akume.storage.catalog.application.exception;

import java.util.UUID;

/** Indicates that an Item snapshot no longer matches its persisted version. */
public class ItemConcurrentModificationException extends RuntimeException {

    public ItemConcurrentModificationException(UUID itemId) {
        super("Item was concurrently modified: " + itemId);
    }
}
