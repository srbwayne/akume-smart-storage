package dev.akume.storage.catalog.application.port.in;

import java.util.UUID;

/** Category reassignment and the Item version observed when the command was initiated. */
public record ReassignItemCategoryCommand(UUID id, UUID itemCategoryId, int expectedVersion) {
}
