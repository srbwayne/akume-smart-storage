package dev.akume.storage.location.application.port.in;

import dev.akume.storage.location.domain.model.AddressType;

/** Creates an address type. */
public interface CreateAddressTypeUseCase {

    AddressType create(CreateAddressTypeCommand command);
}
