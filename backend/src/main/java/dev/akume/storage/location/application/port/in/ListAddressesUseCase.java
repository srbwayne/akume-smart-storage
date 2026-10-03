package dev.akume.storage.location.application.port.in;

import dev.akume.storage.location.domain.model.Address;

import java.util.List;

/** Lists all Addresses without defining an application-level ordering contract. */
public interface ListAddressesUseCase {

    List<Address> listAll();
}
