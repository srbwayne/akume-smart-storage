package dev.akume.storage.location.application.exception;

import java.util.UUID;

/** Indicates that an AddressType used by active Addresses cannot be deactivated. */
public class AddressTypeInUseException extends RuntimeException {

    private final UUID addressTypeId;

    public AddressTypeInUseException(UUID addressTypeId) {
        super("AddressType is used by active Addresses: " + addressTypeId);
        this.addressTypeId = addressTypeId;
    }

    public UUID addressTypeId() {
        return addressTypeId;
    }
}
