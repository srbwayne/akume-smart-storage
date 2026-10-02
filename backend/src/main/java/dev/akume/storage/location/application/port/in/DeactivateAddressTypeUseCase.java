package dev.akume.storage.location.application.port.in;

import dev.akume.storage.location.domain.model.AddressType;

import java.util.UUID;

/** Deactivates an address type. */
public interface DeactivateAddressTypeUseCase {

    AddressType deactivate(UUID id);
}
