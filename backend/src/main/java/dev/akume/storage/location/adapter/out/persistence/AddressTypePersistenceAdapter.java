package dev.akume.storage.location.adapter.out.persistence;

import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.domain.exception.AddressTypeCodeAlreadyExistsException;
import dev.akume.storage.location.domain.model.AddressType;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class AddressTypePersistenceAdapter implements AddressTypeRepository {

    private static final String CODE_UNIQUE_CONSTRAINT = "uk_address_types_code";

    private final SpringDataAddressTypeRepository repository;

    AddressTypePersistenceAdapter(SpringDataAddressTypeRepository repository) {
        this.repository = repository;
    }

    @Override
    public AddressType save(AddressType addressType) {
        try {
            AddressTypeJpaEntity saved = repository.saveAndFlush(toEntity(addressType));
            return toDomain(saved);
        } catch (DataIntegrityViolationException exception) {
            if (violatesCodeUniqueness(exception)) {
                throw new AddressTypeCodeAlreadyExistsException(addressType.code());
            }
            throw exception;
        }
    }

    @Override
    public Optional<AddressType> findById(UUID id) {
        return repository.findById(id).map(AddressTypePersistenceAdapter::toDomain);
    }

    @Override
    public Optional<AddressType> findByCode(String code) {
        return repository.findByCode(code).map(AddressTypePersistenceAdapter::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return repository.existsByCode(code);
    }

    @Override
    public List<AddressType> findAll() {
        return repository.findAll().stream().map(AddressTypePersistenceAdapter::toDomain).toList();
    }

    private static AddressTypeJpaEntity toEntity(AddressType domain) {
        return new AddressTypeJpaEntity(domain.id(), domain.code(), domain.name(), domain.description(), domain.active());
    }

    private static AddressType toDomain(AddressTypeJpaEntity entity) {
        return AddressType.reconstitute(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.isActive());
    }

    private static boolean violatesCodeUniqueness(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return CODE_UNIQUE_CONSTRAINT.equals(constraintViolation.getConstraintName());
            }
        }
        return false;
    }
}
