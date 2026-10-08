package dev.akume.storage.catalog.application.port.in;

import java.util.UUID;

/** Input for creating an Item; identity, lifecycle, and initial version are domain-owned. */
public record CreateItemCommand(String name, String description, UUID itemCategoryId) {
}
