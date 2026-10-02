package dev.akume.storage.location.application.exception;

import java.util.UUID;

public class AddressTypeNotFoundException extends RuntimeException {

    private final UUID addressTypeId;

    public AddressTypeNotFoundException(UUID addressTypeId) {
        super("Address type not found: " + addressTypeId);
        this.addressTypeId = addressTypeId;
    }

    public UUID addressTypeId() {
        return addressTypeId;
    }
}
