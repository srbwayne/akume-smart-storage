package dev.akume.storage.location.application.exception;

import java.util.UUID;

public class AddressAlreadyExistsException extends RuntimeException {

    private final UUID addressId;

    public AddressAlreadyExistsException(UUID addressId) {
        super("Address already exists: " + addressId);
        this.addressId = addressId;
    }

    public UUID addressId() {
        return addressId;
    }
}
