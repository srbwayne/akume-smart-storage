package dev.akume.storage.location.domain.exception;

public class AddressSiblingNameAlreadyExistsException extends RuntimeException {

    public AddressSiblingNameAlreadyExistsException(String name) {
        super("An address with this sibling name already exists: " + name);
    }
}
