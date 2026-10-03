package dev.akume.storage.location.application.port.in;

import java.util.UUID;

/** Caller-supplied values for creating one active Address. */
public record CreateAddressCommand(String name, UUID addressTypeId, UUID parentId) {
}
