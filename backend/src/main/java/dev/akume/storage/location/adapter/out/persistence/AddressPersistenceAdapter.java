package dev.akume.storage.location.adapter.out.persistence;

import dev.akume.storage.location.application.exception.AddressAlreadyExistsException;
import dev.akume.storage.location.application.exception.AddressConcurrentModificationException;
import dev.akume.storage.location.application.exception.AddressNotFoundException;
import dev.akume.storage.location.application.exception.AddressTypeNotFoundException;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.domain.exception.AddressSiblingNameAlreadyExistsException;
import dev.akume.storage.location.domain.model.Address;
import jakarta.persistence.EntityExistsException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
class AddressPersistenceAdapter implements AddressRepository {

    private static final String PRIMARY_KEY = "pk_addresses";
    private static final String ROOT_NAME_UNIQUE = "uk_addresses_root_normalized_name_key";
    private static final String CHILD_NAME_UNIQUE = "uk_addresses_parent_normalized_name_key";
    private static final String ADDRESS_TYPE_FOREIGN_KEY = "fk_addresses_address_type_id";
    private static final String PARENT_FOREIGN_KEY = "fk_addresses_parent_id";

    private final SpringDataAddressRepository repository;

    AddressPersistenceAdapter(SpringDataAddressRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Address insert(Address address) {
        if (address.version() != 0) {
            throw new IllegalArgumentException("a new address must have version 0");
        }

        try {
            // Integer @Version is null only on this new entity, so Spring Data calls persist.
            AddressJpaEntity inserted = repository.saveAndFlush(AddressJpaEntity.newEntity(address));
            return toDomain(inserted);
        } catch (EntityExistsException exception) {
            throw new AddressAlreadyExistsException(address.id());
        } catch (DataIntegrityViolationException exception) {
            throw translateIntegrityFailure(exception, address);
        }
    }

    @Override
    @Transactional
    public Address update(Address address) {
        AddressJpaEntity managed = repository.findById(address.id())
                .orElseThrow(() -> new AddressNotFoundException(address.id()));

        if (managed.getVersion() == null || managed.getVersion() != address.version()) {
            throw new AddressConcurrentModificationException(address.id());
        }
        if (!managed.getAddressTypeId().equals(address.addressTypeId())) {
            throw new IllegalArgumentException("addressTypeId cannot be changed");
        }

        managed.copyMutableStateFrom(address);
        try {
            repository.flush();
            return toDomain(managed);
        } catch (OptimisticLockingFailureException exception) {
            throw new AddressConcurrentModificationException(address.id());
        } catch (DataIntegrityViolationException exception) {
            throw translateIntegrityFailure(exception, address);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Address> findById(UUID id) {
        return repository.findById(id).map(AddressPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Address> findAll() {
        return repository.findAllInIdOrder().stream()
                .map(AddressPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Address> findRoots() {
        return repository.findRootAddresses().stream()
                .map(AddressPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Address> findDirectChildren(UUID parentId) {
        return repository.findDirectChildAddresses(parentId).stream()
                .map(AddressPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsRootNameKey(String normalizedNameKey, UUID excludedAddressId) {
        Objects.requireNonNull(excludedAddressId, "excludedAddressId must not be null");
        return repository.existsByParentIdIsNullAndNormalizedNameKeyAndIdNot(
                normalizedNameKey,
                excludedAddressId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsChildNameKey(
            UUID parentId,
            String normalizedNameKey,
            UUID excludedAddressId) {
        Objects.requireNonNull(excludedAddressId, "excludedAddressId must not be null");
        return repository.existsByParentIdAndNormalizedNameKeyAndIdNot(
                parentId,
                normalizedNameKey,
                excludedAddressId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isDescendant(UUID ancestorId, UUID candidateId) {
        return repository.isStrictDescendant(ancestorId, candidateId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveDirectChild(UUID parentId) {
        return repository.existsByParentIdAndActiveTrue(parentId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActiveByAddressTypeId(UUID addressTypeId) {
        Objects.requireNonNull(addressTypeId, "addressTypeId must not be null");
        return repository.existsByAddressTypeIdAndActiveTrue(addressTypeId);
    }

    private static Address toDomain(AddressJpaEntity entity) {
        if (entity.getVersion() == null) {
            throw new IllegalStateException("persisted Address has no version");
        }
        return Address.reconstitute(
                entity.getId(),
                entity.getName(),
                entity.getNormalizedNameKey(),
                entity.getAddressTypeId(),
                entity.getParentId(),
                entity.isActive(),
                entity.getVersion());
    }

    private static RuntimeException translateIntegrityFailure(
            DataIntegrityViolationException failure,
            Address address) {
        String constraint = constraintName(failure);
        if (PRIMARY_KEY.equals(constraint)) {
            return new AddressAlreadyExistsException(address.id());
        }
        if (ROOT_NAME_UNIQUE.equals(constraint) || CHILD_NAME_UNIQUE.equals(constraint)) {
            return new AddressSiblingNameAlreadyExistsException(address.name());
        }
        if (ADDRESS_TYPE_FOREIGN_KEY.equals(constraint)) {
            return new AddressTypeNotFoundException(address.addressTypeId());
        }
        if (PARENT_FOREIGN_KEY.equals(constraint)) {
            return new AddressNotFoundException(address.parentId());
        }
        return failure;
    }

    private static String constraintName(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }
}
