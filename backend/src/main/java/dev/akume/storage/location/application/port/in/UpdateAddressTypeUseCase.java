package dev.akume.storage.location.application.port.in;

import dev.akume.storage.location.domain.model.AddressType;

/** Updates the descriptive details of an address type. */
public interface UpdateAddressTypeUseCase {

    AddressType update(UpdateAddressTypeCommand command);
}
