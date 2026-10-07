package dev.akume.storage.catalog.application.port.in;

import java.util.UUID;

/** Deactivation request and the category version observed when the command was initiated. */
public record DeactivateItemCategoryCommand(UUID id, int expectedVersion) {
}
