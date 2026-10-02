package dev.akume.storage.location.application.port.in;

import dev.akume.storage.location.domain.model.AddressType;

import java.util.UUID;

/** Activates an address type. */
public interface ActivateAddressTypeUseCase {

    AddressType activate(UUID id);
}
