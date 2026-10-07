package dev.akume.storage.catalog.application.port.in;

/** Input for creating an ItemCategory. Identity, lifecycle, and initial version are domain-owned. */
public record CreateItemCategoryCommand(String name) {
}
