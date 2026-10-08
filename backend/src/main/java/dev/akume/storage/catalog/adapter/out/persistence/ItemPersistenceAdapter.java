package dev.akume.storage.catalog.adapter.out.persistence;

import dev.akume.storage.catalog.application.exception.ItemConcurrentModificationException;
import dev.akume.storage.catalog.application.port.out.ItemRepository;
import dev.akume.storage.catalog.domain.model.Item;
import jakarta.persistence.EntityExistsException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ItemPersistenceAdapter implements ItemRepository {

    private final SpringDataItemRepository repository;

    ItemPersistenceAdapter(SpringDataItemRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Item insert(Item item) {
        if (item.version() != 0) {
            throw new IllegalArgumentException("a new Item must have version 0");
        }
        try {
            ItemJpaEntity inserted = repository.saveAndFlush(ItemJpaEntity.newEntity(item));
            return toDomain(inserted);
        } catch (EntityExistsException exception) {
            throw exception;
        }
    }

    @Override
    @Transactional
    public Optional<Item> update(Item item) {
        Optional<ItemJpaEntity> found = repository.findById(item.id());
        if (found.isEmpty()) {
            return Optional.empty();
        }

        ItemJpaEntity managed = found.orElseThrow();
        if (managed.getVersion() == null || managed.getVersion() != item.version()) {
            throw new ItemConcurrentModificationException(item.id());
        }

        managed.copyMutableStateFrom(item);
        try {
            repository.flush();
            return Optional.of(toDomain(managed));
        } catch (OptimisticLockingFailureException exception) {
            throw new ItemConcurrentModificationException(item.id());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Item> findById(UUID id) {
        return repository.findById(id).map(ItemPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Item> findAll() {
        return repository.findAll().stream()
                .map(ItemPersistenceAdapter::toDomain)
                .toList();
    }

    private static Item toDomain(ItemJpaEntity entity) {
        if (entity.getVersion() == null) {
            throw new IllegalStateException("persisted Item has no version");
        }
        return Item.reconstitute(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getItemCategoryId(),
                entity.isActive(),
                entity.getVersion());
    }
}
