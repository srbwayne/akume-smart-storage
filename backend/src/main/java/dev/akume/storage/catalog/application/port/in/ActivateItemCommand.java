package dev.akume.storage.catalog.application.port.in;

import java.util.UUID;

/** Activation request and the Item version observed when the command was initiated. */
public record ActivateItemCommand(UUID id, int expectedVersion) {
}
