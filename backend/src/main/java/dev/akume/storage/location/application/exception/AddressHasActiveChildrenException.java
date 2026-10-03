package dev.akume.storage.location.application.exception;

import java.util.UUID;

/** Indicates that an Address with active direct children cannot be deactivated. */
public class AddressHasActiveChildrenException extends RuntimeException {

    private final UUID addressId;

    public AddressHasActiveChildrenException(UUID addressId) {
        super("Address has active direct children: " + addressId);
        this.addressId = addressId;
    }

    public UUID addressId() {
        return addressId;
    }
}
