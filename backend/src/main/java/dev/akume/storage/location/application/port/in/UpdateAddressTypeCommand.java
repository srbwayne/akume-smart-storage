package dev.akume.storage.location.application.port.in;

import java.util.UUID;

/** Editable address type details and the identity of the existing type. */
public record UpdateAddressTypeCommand(UUID id, String name, String description) {
}
