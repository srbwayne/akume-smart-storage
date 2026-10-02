package dev.akume.storage.location.application.port.in;

import dev.akume.storage.location.domain.model.AddressType;

import java.util.UUID;

/** Retrieves one address type by its identity. */
public interface GetAddressTypeUseCase {

    AddressType getById(UUID id);
}
