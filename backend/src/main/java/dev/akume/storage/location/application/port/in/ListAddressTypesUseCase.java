package dev.akume.storage.location.application.port.in;

import dev.akume.storage.location.domain.model.AddressType;

import java.util.List;

/** Lists all address types. */
public interface ListAddressTypesUseCase {

    List<AddressType> listAll();
}
