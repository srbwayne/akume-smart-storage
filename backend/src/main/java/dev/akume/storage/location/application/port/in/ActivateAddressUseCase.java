package dev.akume.storage.location.application.port.in;

import dev.akume.storage.location.domain.model.Address;

/** Activates one Address after validating its active Type and parent prerequisites. */
public interface ActivateAddressUseCase {

    Address activate(ActivateAddressCommand command);
}
