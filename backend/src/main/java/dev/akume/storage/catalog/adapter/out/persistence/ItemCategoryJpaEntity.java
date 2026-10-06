package dev.akume.storage.catalog.adapter.out.persistence;

import dev.akume.storage.catalog.domain.model.ItemCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "item_categories")
class ItemCategoryJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "normalized_name_key", nullable = false)
    private String normalizedNameKey;

    @Column(nullable = false)
    private boolean active;

    @Version
    @Column(nullable = false)
    private Integer version;

    protected ItemCategoryJpaEntity() {
    }

    private ItemCategoryJpaEntity(
            UUID id,
            String name,
            String normalizedNameKey,
            boolean active,
            Integer version) {
        this.id = id;
        this.name = name;
        this.normalizedNameKey = normalizedNameKey;
        this.active = active;
        this.version = version;
    }

    static ItemCategoryJpaEntity newEntity(ItemCategory category) {
        // The domain creation version is zero; null is Hibernate's transient @Version marker,
        // and the provider initializes the persisted version to zero on insert.
        return new ItemCategoryJpaEntity(
                category.id(),
                category.name(),
                category.canonicalNameKey(),
                category.active(),
                null);
    }

    void copyMutableStateFrom(ItemCategory category) {
        this.name = category.name();
        this.normalizedNameKey = category.canonicalNameKey();
        this.active = category.active();
    }

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getNormalizedNameKey() {
        return normalizedNameKey;
    }

    boolean isActive() {
        return active;
    }

    Integer getVersion() {
        return version;
    }
}
