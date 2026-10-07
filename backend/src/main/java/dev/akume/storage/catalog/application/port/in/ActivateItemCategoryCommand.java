package dev.akume.storage.catalog.application.port.in;

import java.util.UUID;

/** Activation request and the category version observed when the command was initiated. */
public record ActivateItemCategoryCommand(UUID id, int expectedVersion) {
}
