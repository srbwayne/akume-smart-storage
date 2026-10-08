package dev.akume.storage.catalog.application.port.in;

import java.util.UUID;

/** Deactivation request and the Item version observed when the command was initiated. */
public record DeactivateItemCommand(UUID id, int expectedVersion) {
}
