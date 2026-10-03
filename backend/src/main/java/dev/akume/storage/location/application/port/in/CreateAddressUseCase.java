package dev.akume.storage.location.application.port.in;

import dev.akume.storage.location.domain.model.Address;

/** Creates a root or child Address. */
public interface CreateAddressUseCase {

    Address create(CreateAddressCommand command);
}
