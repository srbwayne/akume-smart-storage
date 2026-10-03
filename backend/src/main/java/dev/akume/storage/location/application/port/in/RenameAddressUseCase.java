package dev.akume.storage.location.application.port.in;

import dev.akume.storage.location.domain.model.Address;

/** Renames an Address without changing its type, parent, or lifecycle state. */
public interface RenameAddressUseCase {

    Address rename(RenameAddressCommand command);
}
