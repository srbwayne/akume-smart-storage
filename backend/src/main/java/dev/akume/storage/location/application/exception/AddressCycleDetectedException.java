package dev.akume.storage.location.application.exception;

import java.util.UUID;

/** Indicates that an Address move would make the Address its own ancestor. */
public class AddressCycleDetectedException extends RuntimeException {

    private final UUID addressId;
    private final UUID proposedParentId;

    public AddressCycleDetectedException(UUID addressId, UUID proposedParentId) {
        super("Moving Address would create a hierarchy cycle: " + addressId);
        this.addressId = addressId;
        this.proposedParentId = proposedParentId;
    }

    public UUID addressId() {
        return addressId;
    }

    public UUID proposedParentId() {
        return proposedParentId;
    }
}
