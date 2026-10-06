package dev.akume.storage.catalog.application.exception;

import java.util.UUID;

/** Indicates that a category changed after the supplied persistence version was read. */
public class ItemCategoryConcurrentModificationException extends RuntimeException {

    public ItemCategoryConcurrentModificationException(UUID id) {
        super("Item category was concurrently modified: " + id);
    }
}
