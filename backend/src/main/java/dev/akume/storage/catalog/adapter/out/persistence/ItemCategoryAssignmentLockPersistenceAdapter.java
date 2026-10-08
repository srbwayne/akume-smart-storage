package dev.akume.storage.catalog.adapter.out.persistence;

import dev.akume.storage.catalog.application.port.out.ItemCategoryAssignmentLock;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class ItemCategoryAssignmentLockPersistenceAdapter implements ItemCategoryAssignmentLock {

    private final SpringDataItemCategoryLockRepository repository;

    ItemCategoryAssignmentLockPersistenceAdapter(SpringDataItemCategoryLockRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Boolean> lockAndReadActive(UUID itemCategoryId) {
        return repository.lockAndReadActive(itemCategoryId);
    }
}
