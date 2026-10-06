package dev.akume.storage.catalog.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataItemCategoryRepository extends JpaRepository<ItemCategoryJpaEntity, UUID> {
}
