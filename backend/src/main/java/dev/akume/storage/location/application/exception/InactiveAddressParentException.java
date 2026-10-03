package dev.akume.storage.location.application.exception;

import java.util.UUID;

/** Indicates that an active Address cannot have an inactive parent. */
public class InactiveAddressParentException extends RuntimeException {

    private final UUID parentId;

    public InactiveAddressParentException(UUID parentId) {
        super("An active Address requires an active parent: " + parentId);
        this.parentId = parentId;
    }

    public UUID parentId() {
        return parentId;
    }
}
