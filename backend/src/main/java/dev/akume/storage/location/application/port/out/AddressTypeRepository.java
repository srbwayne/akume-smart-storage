package dev.akume.storage.location.application.port.out;

import dev.akume.storage.location.domain.model.AddressType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence operations required by address type application workflows. */
public interface AddressTypeRepository {

    AddressType save(AddressType addressType);

    Optional<AddressType> findById(UUID id);

    Optional<AddressType> findByCode(String code);

    boolean existsByCode(String code);

    List<AddressType> findAll();
}
