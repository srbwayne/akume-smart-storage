package dev.akume.storage.location.application.port.in;

import java.util.UUID;

/** Requests activation of one Address with optimistic version protection. */
public record ActivateAddressCommand(UUID addressId, int expectedVersion) {
}
