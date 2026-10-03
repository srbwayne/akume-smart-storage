package dev.akume.storage.location.application.port.in;

import java.util.UUID;

/** Requests deactivation of one Address with optimistic version protection. */
public record DeactivateAddressCommand(UUID addressId, int expectedVersion) {
}
