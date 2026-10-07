package dev.akume.storage.catalog.application.port.in;

import java.util.UUID;

/** Name change and the category version observed when the command was initiated. */
public record RenameItemCategoryCommand(UUID id, String name, int expectedVersion) {
}
