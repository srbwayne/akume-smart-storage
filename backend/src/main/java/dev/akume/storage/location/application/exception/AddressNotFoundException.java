package dev.akume.storage.location.application.exception;

import java.util.UUID;

public class AddressNotFoundException extends RuntimeException {

    private final UUID addressId;

    public AddressNotFoundException(UUID addressId) {
        super("Address not found: " + addressId);
        this.addressId = addressId;
    }

    public UUID addressId() {
        return addressId;
    }
}
