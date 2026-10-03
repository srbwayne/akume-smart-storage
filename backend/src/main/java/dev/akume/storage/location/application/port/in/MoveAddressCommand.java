package dev.akume.storage.location.application.port.in;

import java.util.UUID;

/** Requests a parent change for an Address with optimistic version protection. */
public record MoveAddressCommand(UUID addressId, UUID newParentId, int expectedVersion) {
}
