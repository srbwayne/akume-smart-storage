package dev.akume.storage.catalog.adapter.out.persistence;

import dev.akume.storage.catalog.application.exception.ItemCategoryConcurrentModificationException;
import dev.akume.storage.catalog.application.exception.ItemCategoryNameAlreadyExistsException;
import dev.akume.storage.catalog.application.port.out.ItemCategoryRepository;
import dev.akume.storage.catalog.domain.model.ItemCategory;
import jakarta.persistence.EntityExistsException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ItemCategoryPersistenceAdapter implements ItemCategoryRepository {

    private static final String NAME_UNIQUE_CONSTRAINT = "uk_item_categories_normalized_name_key";

    private final SpringDataItemCategoryRepository repository;

    ItemCategoryPersistenceAdapter(SpringDataItemCategoryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ItemCategory insert(ItemCategory category) {
        if (category.version() != 0) {
            throw new IllegalArgumentException("a new ItemCategory must have version 0");
        }

        try {
            ItemCategoryJpaEntity inserted = repository.saveAndFlush(ItemCategoryJpaEntity.newEntity(category));
            return toDomain(inserted);
        } catch (EntityExistsException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            throw translateIntegrityFailure(exception, category);
        }
    }

    @Override
    @Transactional
    public Optional<ItemCategory> update(ItemCategory category) {
        Optional<ItemCategoryJpaEntity> found = repository.findById(category.id());
        if (found.isEmpty()) {
            return Optional.empty();
        }

        ItemCategoryJpaEntity managed = found.orElseThrow();
        if (managed.getVersion() == null || managed.getVersion() != category.version()) {
            throw new ItemCategoryConcurrentModificationException(category.id());
        }

        managed.copyMutableStateFrom(category);
        try {
            repository.flush();
            return Optional.of(toDomain(managed));
        } catch (OptimisticLockingFailureException exception) {
            throw new ItemCategoryConcurrentModificationException(category.id());
        } catch (DataIntegrityViolationException exception) {
            throw translateIntegrityFailure(exception, category);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ItemCategory> findById(UUID id) {
        return repository.findById(id).map(ItemCategoryPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemCategory> findAll() {
        return repository.findAll().stream()
                .map(ItemCategoryPersistenceAdapter::toDomain)
                .toList();
    }

    private static ItemCategory toDomain(ItemCategoryJpaEntity entity) {
        if (entity.getVersion() == null) {
            throw new IllegalStateException("persisted ItemCategory has no version");
        }
        return ItemCategory.reconstitute(
                entity.getId(),
                entity.getName(),
                entity.isActive(),
                entity.getVersion());
    }

    private static RuntimeException translateIntegrityFailure(
            DataIntegrityViolationException failure,
            ItemCategory category) {
        if (NAME_UNIQUE_CONSTRAINT.equals(constraintName(failure))) {
            return new ItemCategoryNameAlreadyExistsException(category.name());
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
