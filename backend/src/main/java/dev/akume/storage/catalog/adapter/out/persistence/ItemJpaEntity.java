package dev.akume.storage.catalog.adapter.out.persistence;

import dev.akume.storage.catalog.domain.model.Item;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "items")
class ItemJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, columnDefinition = "text")
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "item_category_id", nullable = false)
    private UUID itemCategoryId;

    @Column(nullable = false)
    private boolean active;

    @Version
    @Column(nullable = false)
    private Integer version;

    protected ItemJpaEntity() {
    }

    private ItemJpaEntity(
            UUID id,
            String name,
            String description,
            UUID itemCategoryId,
            boolean active,
            Integer version) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.itemCategoryId = itemCategoryId;
        this.active = active;
        this.version = version;
    }

    static ItemJpaEntity newEntity(Item item) {
        // Null is Hibernate's transient @Version marker; the provider stores version zero.
        return new ItemJpaEntity(
                item.id(),
                item.name(),
                item.description(),
                item.itemCategoryId(),
                item.active(),
                null);
    }

    void copyMutableStateFrom(Item item) {
        this.name = item.name();
        this.description = item.description();
        this.itemCategoryId = item.itemCategoryId();
        this.active = item.active();
    }

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getDescription() {
        return description;
    }

    UUID getItemCategoryId() {
        return itemCategoryId;
    }

    boolean isActive() {
        return active;
    }

    Integer getVersion() {
        return version;
    }
}
