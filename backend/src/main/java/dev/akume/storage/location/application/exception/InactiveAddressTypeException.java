package dev.akume.storage.location.application.exception;

import java.util.UUID;

/** Indicates that a new Address cannot use an inactive AddressType. */
public class InactiveAddressTypeException extends RuntimeException {

    private final UUID addressTypeId;

    public InactiveAddressTypeException(UUID addressTypeId) {
        super("Address type is inactive: " + addressTypeId);
        this.addressTypeId = addressTypeId;
    }

    public UUID addressTypeId() {
        return addressTypeId;
    }
}
