package dev.akume.storage.location.application.exception;

import java.util.UUID;

public class AddressConcurrentModificationException extends RuntimeException {

    private final UUID addressId;

    public AddressConcurrentModificationException(UUID addressId) {
        super("Address was concurrently modified: " + addressId);
        this.addressId = addressId;
    }

    public UUID addressId() {
        return addressId;
    }
}
