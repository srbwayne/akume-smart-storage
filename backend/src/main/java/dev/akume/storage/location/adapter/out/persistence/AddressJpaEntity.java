package dev.akume.storage.location.adapter.out.persistence;

import dev.akume.storage.location.domain.model.Address;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "addresses")
class AddressJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "normalized_name_key", nullable = false)
    private String normalizedNameKey;

    @Column(name = "address_type_id", nullable = false)
    private UUID addressTypeId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private boolean active;

    @Version
    @Column(nullable = false)
    private Integer version;

    protected AddressJpaEntity() {
    }

    AddressJpaEntity(
            UUID id,
            String name,
            String normalizedNameKey,
            UUID addressTypeId,
            UUID parentId,
            boolean active) {
        this.id = id;
        this.name = name;
        this.normalizedNameKey = normalizedNameKey;
        this.addressTypeId = addressTypeId;
        this.parentId = parentId;
        this.active = active;
        // A null version marks a new persistence entity; the provider initializes it on insert.
    }

    static AddressJpaEntity newEntity(Address address) {
        return new AddressJpaEntity(
                address.id(),
                address.name(),
                address.normalizedNameKey(),
                address.addressTypeId(),
                address.parentId(),
                address.active());
    }

    void copyMutableStateFrom(Address address) {
        this.name = address.name();
        this.normalizedNameKey = address.normalizedNameKey();
        this.parentId = address.parentId();
        this.active = address.active();
    }

    UUID getId() { return id; }
    String getName() { return name; }
    String getNormalizedNameKey() { return normalizedNameKey; }
    UUID getAddressTypeId() { return addressTypeId; }
    UUID getParentId() { return parentId; }
    boolean isActive() { return active; }
    Integer getVersion() { return version; }
}
