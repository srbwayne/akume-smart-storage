package dev.akume.storage.catalog.adapter.out.persistence;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/** Narrow Spring Data query for the transactional category assignment lock. */
interface SpringDataItemCategoryLockRepository extends Repository<ItemCategoryJpaEntity, UUID> {

    @Query(value = "SELECT active FROM item_categories WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Boolean> lockAndReadActive(@Param("id") UUID id);
}
