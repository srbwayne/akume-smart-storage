package dev.akume.storage.location.application.exception;

import java.util.UUID;

/** Indicates that an active child Address cannot be created below an inactive parent. */
public class InactiveAddressParentException extends RuntimeException {

    private final UUID parentId;

    public InactiveAddressParentException(UUID parentId) {
        super("Address parent is inactive: " + parentId);
        this.parentId = parentId;
    }

    public UUID parentId() {
        return parentId;
    }
}
