package dev.akume.storage.catalog.application.port.in;

import java.util.UUID;

/** Metadata update and the Item version observed when the command was initiated. */
public record UpdateItemMetadataCommand(
        UUID id,
        String name,
        String description,
        int expectedVersion) {
}
