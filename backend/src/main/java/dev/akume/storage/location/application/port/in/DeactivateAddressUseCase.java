package dev.akume.storage.location.application.port.in;

import dev.akume.storage.location.domain.model.Address;

/** Deactivates one Address without cascading to its descendants. */
public interface DeactivateAddressUseCase {

    Address deactivate(DeactivateAddressCommand command);
}
