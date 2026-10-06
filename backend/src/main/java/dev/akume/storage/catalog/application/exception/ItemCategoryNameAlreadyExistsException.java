package dev.akume.storage.catalog.application.exception;

/** Indicates that another category already reserves the canonical name key. */
public class ItemCategoryNameAlreadyExistsException extends RuntimeException {

    public ItemCategoryNameAlreadyExistsException(String name) {
        super("An item category with this name already exists: " + name);
    }
}
