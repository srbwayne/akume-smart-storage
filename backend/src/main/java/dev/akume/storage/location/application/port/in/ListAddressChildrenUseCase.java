package dev.akume.storage.location.application.port.in;

import dev.akume.storage.location.domain.model.Address;

import java.util.List;
import java.util.UUID;

/** Lists the direct children of an existing Address. */
public interface ListAddressChildrenUseCase {

    List<Address> listDirectChildren(UUID parentId);
}
