package dev.akume.storage.location.application.port.in;

import dev.akume.storage.location.domain.model.Address;

/** Moves an Address while preserving its subtree. */
public interface MoveAddressUseCase {

    Address move(MoveAddressCommand command);
}
