package dev.akume.storage.location.application.port.in;

import dev.akume.storage.location.domain.model.Address;

import java.util.List;

/** Lists root Addresses without loading their descendants. */
public interface ListAddressRootsUseCase {

    List<Address> listRoots();
}
