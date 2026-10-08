package dev.akume.storage.catalog.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataItemRepository extends JpaRepository<ItemJpaEntity, UUID> {
}
