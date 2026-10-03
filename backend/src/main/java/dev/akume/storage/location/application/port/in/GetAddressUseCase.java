package dev.akume.storage.location.application.port.in;

import dev.akume.storage.location.domain.model.Address;

import java.util.UUID;

/** Retrieves one Address by its identity. */
public interface GetAddressUseCase {

    Address getById(UUID id);
}
