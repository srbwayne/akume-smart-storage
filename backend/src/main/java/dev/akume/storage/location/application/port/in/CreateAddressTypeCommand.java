package dev.akume.storage.location.application.port.in;

/** Input values for creating an address type. Identity and active state are domain-owned. */
public record CreateAddressTypeCommand(String code, String name, String description) {
}
