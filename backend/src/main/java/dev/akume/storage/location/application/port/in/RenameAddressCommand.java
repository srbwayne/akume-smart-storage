package dev.akume.storage.location.application.port.in;

import java.util.UUID;

/** Name change for an existing Address, guarded by its expected persistence version. */
public record RenameAddressCommand(UUID addressId, String name, int expectedVersion) {
}
