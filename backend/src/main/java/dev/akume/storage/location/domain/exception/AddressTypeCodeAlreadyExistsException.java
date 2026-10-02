package dev.akume.storage.location.domain.exception;

public class AddressTypeCodeAlreadyExistsException extends RuntimeException {

    public AddressTypeCodeAlreadyExistsException(String code) {
        super("Address type code already exists: " + code);
    }
}
