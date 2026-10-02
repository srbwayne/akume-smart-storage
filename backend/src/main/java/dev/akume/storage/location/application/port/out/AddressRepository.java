package dev.akume.storage.location.application.port.out;

import dev.akume.storage.location.domain.model.Address;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence operations required by Address creation and persistence-backed reads/updates. */
public interface AddressRepository {

    Address insert(Address address);

    Address update(Address address);

    Optional<Address> findById(UUID id);

    List<Address> findAll();

    List<Address> findRoots();

    List<Address> findDirectChildren(UUID parentId);

    boolean existsRootNameKey(String normalizedNameKey, UUID excludedAddressId);

    boolean existsChildNameKey(UUID parentId, String normalizedNameKey, UUID excludedAddressId);

    boolean isDescendant(UUID ancestorId, UUID candidateId);

    boolean hasActiveDirectChild(UUID parentId);
}
