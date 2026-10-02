package dev.akume.storage.location.adapter.out.persistence;

import dev.akume.storage.location.application.exception.AddressAlreadyExistsException;
import dev.akume.storage.location.application.exception.AddressConcurrentModificationException;
import dev.akume.storage.location.application.exception.AddressNotFoundException;
import dev.akume.storage.location.application.exception.AddressTypeNotFoundException;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.domain.exception.AddressSiblingNameAlreadyExistsException;
import dev.akume.storage.location.domain.model.Address;
import dev.akume.storage.location.domain.model.AddressType;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AddressPersistenceIntegrationTest {

    @Autowired
    private AddressRepository addresses;

    @Autowired
    private SpringDataAddressRepository jpaAddresses;

    @Autowired
    private AddressTypeRepository addressTypes;

    @Autowired
    private SpringDataAddressTypeRepository jpaAddressTypes;

    @BeforeEach
    void clearExistingRows() {
        clearRows();
    }

    @AfterEach
    void clearTestRows() {
        clearRows();
    }

    @Test
    void insertsAndReloadsRootWithProviderInitializedVersionZero() {
        AddressType type = createAddressType();
        Address created = Address.create("Home", type.id(), null);

        Address inserted = addresses.insert(created);
        AddressJpaEntity row = jpaAddresses.findById(created.id()).orElseThrow();
        Address reloaded = addresses.findById(created.id()).orElseThrow();

        assertAddressState(inserted, created.id(), "Home", "home", type.id(), null, true, 0);
        assertAddressState(reloaded, created.id(), "Home", "home", type.id(), null, true, 0);
        assertEquals(0, row.getVersion());
    }

    @Test
    void roundTripsChildAndInactiveAddressState() {
        AddressType type = createAddressType();
        Address parent = addresses.insert(Address.create("Room", type.id(), null));
        UUID childId = UUID.randomUUID();
        Address inactiveChild = Address.reconstitute(
                childId, "Box", "box", type.id(), parent.id(), false, 0);

        Address inserted = addresses.insert(inactiveChild);
        Address reloaded = addresses.findById(childId).orElseThrow();

        assertAddressState(inserted, childId, "Box", "box", type.id(), parent.id(), false, 0);
        assertAddressState(reloaded, childId, "Box", "box", type.id(), parent.id(), false, 0);
    }

    @Test
    void updatesManagedAddressAndProviderIncrementsVersion() {
        AddressType type = createAddressType();
        Address inserted = addresses.insert(Address.create("Drawer", type.id(), null));
        inserted.updateDetails("Storage Drawer");

        Address updated = addresses.update(inserted);
        AddressJpaEntity row = jpaAddresses.findById(inserted.id()).orElseThrow();

        assertEquals("Storage Drawer", updated.name());
        assertEquals("storage drawer", updated.normalizedNameKey());
        assertEquals(1, updated.version());
        assertEquals(1, row.getVersion());
    }

    @Test
    void staleUpdateConflictsWithoutOverwritingOrInserting() {
        AddressType type = createAddressType();
        Address inserted = addresses.insert(Address.create("Drawer", type.id(), null));
        Address firstCopy = addresses.findById(inserted.id()).orElseThrow();
        Address staleCopy = addresses.findById(inserted.id()).orElseThrow();
        firstCopy.updateDetails("First Update");
        staleCopy.updateDetails("Stale Update");

        Address updated = addresses.update(firstCopy);
        assertThrows(AddressConcurrentModificationException.class, () -> addresses.update(staleCopy));

        Address reloaded = addresses.findById(inserted.id()).orElseThrow();
        assertEquals("First Update", reloaded.name());
        assertEquals(updated.version(), reloaded.version());
        assertEquals(1, jpaAddresses.count());
    }

    @Test
    void missingUpdateFailsWithoutInsertingTheAddress() {
        AddressType type = createAddressType();
        UUID missingId = UUID.randomUUID();
        Address missing = Address.reconstitute(missingId, "Missing", "missing", type.id(), null, true, 0);

        assertThrows(AddressNotFoundException.class, () -> addresses.update(missing));

        assertFalse(jpaAddresses.existsById(missingId));
        assertEquals(0, jpaAddresses.count());
    }

    @Test
    void duplicateInsertFailsWithoutUpdatingExistingRow() {
        AddressType type = createAddressType();
        Address existing = addresses.insert(Address.create("Original", type.id(), null));
        Address duplicateId = Address.reconstitute(
                existing.id(), "Replacement", "replacement", type.id(), null, false, 0);

        assertThrows(AddressAlreadyExistsException.class, () -> addresses.insert(duplicateId));

        Address reloaded = addresses.findById(existing.id()).orElseThrow();
        assertEquals("Original", reloaded.name());
        assertTrue(reloaded.active());
        assertEquals(1, jpaAddresses.count());
    }

    @Test
    void translatesUnknownAddressTypeForeignKeyToSemanticNotFound() {
        Address missingType = Address.create("Drawer", UUID.randomUUID(), null);

        assertThrows(AddressTypeNotFoundException.class, () -> addresses.insert(missingType));
        assertEquals(0, jpaAddresses.count());
    }

    @Test
    void translatesUnknownParentForeignKeyToSemanticNotFound() {
        AddressType type = createAddressType();
        UUID missingParentId = UUID.randomUUID();
        Address missingParent = Address.create("Drawer", type.id(), missingParentId);

        AddressNotFoundException failure = assertThrows(
                AddressNotFoundException.class, () -> addresses.insert(missingParent));

        assertEquals(missingParentId, failure.addressId());
        assertEquals(0, jpaAddresses.count());
    }

    @Test
    void databaseRejectsSelfParentEvenWhenDomainValidationIsBypassed() {
        AddressType type = createAddressType();
        UUID id = UUID.randomUUID();
        AddressJpaEntity selfParent = new AddressJpaEntity(id, "Self", "self", type.id(), id, true);

        DataIntegrityViolationException failure = assertThrows(
                DataIntegrityViolationException.class, () -> jpaAddresses.saveAndFlush(selfParent));

        assertEquals("ck_addresses_parent_not_self", constraintName(failure));
    }

    @Test
    void rootSiblingKeyIsUniqueAcrossActiveRows() {
        AddressType type = createAddressType();
        addresses.insert(Address.create("Drawer", type.id(), null));

        AddressSiblingNameAlreadyExistsException failure = assertThrows(
                AddressSiblingNameAlreadyExistsException.class,
                () -> addresses.insert(Address.create("drawer", type.id(), null)));

        assertNotNull(failure.getMessage());
        assertEquals(1, jpaAddresses.count());
    }

    @Test
    void rootSiblingKeyIsUniqueAcrossActiveAndInactiveRows() {
        AddressType type = createAddressType();
        addresses.insert(Address.create("Drawer", type.id(), null));
        Address inactiveDuplicate = Address.reconstitute(
                UUID.randomUUID(), "drawer", "drawer", type.id(), null, false, 0);

        assertThrows(AddressSiblingNameAlreadyExistsException.class,
                () -> addresses.insert(inactiveDuplicate));
        assertEquals(1, jpaAddresses.count());
    }

    @Test
    void childSiblingKeyIsUniqueUnderSameParent() {
        AddressType type = createAddressType();
        Address parent = addresses.insert(Address.create("Room", type.id(), null));
        addresses.insert(Address.create("Drawer", type.id(), parent.id()));

        assertThrows(AddressSiblingNameAlreadyExistsException.class,
                () -> addresses.insert(Address.create("drawer", type.id(), parent.id())));
        assertEquals(2, jpaAddresses.count());
    }

    @Test
    void sameChildNameKeyIsAllowedUnderDifferentParents() {
        AddressType type = createAddressType();
        Address firstParent = addresses.insert(Address.create("Office", type.id(), null));
        Address secondParent = addresses.insert(Address.create("Bedroom", type.id(), null));

        Address firstChild = addresses.insert(Address.create("Drawer 01", type.id(), firstParent.id()));
        Address secondChild = addresses.insert(Address.create("drawer 01", type.id(), secondParent.id()));

        assertEquals(firstParent.id(), firstChild.parentId());
        assertEquals(secondParent.id(), secondChild.parentId());
        assertEquals(4, jpaAddresses.count());
    }

    @Test
    void childSiblingKeyIsUniqueAcrossActiveAndInactiveRows() {
        AddressType type = createAddressType();
        Address parent = addresses.insert(Address.create("Room", type.id(), null));
        addresses.insert(Address.create("Drawer", type.id(), parent.id()));
        Address inactiveDuplicate = Address.reconstitute(
                UUID.randomUUID(), "drawer", "drawer", type.id(), parent.id(), false, 0);

        assertThrows(AddressSiblingNameAlreadyExistsException.class,
                () -> addresses.insert(inactiveDuplicate));
        assertEquals(2, jpaAddresses.count());
    }

    @Test
    void persistenceUniqueIndexRejectsRenameCollision() {
        AddressType type = createAddressType();
        Address parent = addresses.insert(Address.create("Room", type.id(), null));
        addresses.insert(Address.create("Drawer", type.id(), parent.id()));
        Address other = addresses.insert(Address.create("Shelf", type.id(), parent.id()));
        other.updateDetails("drawer");

        assertThrows(AddressSiblingNameAlreadyExistsException.class, () -> addresses.update(other));
        assertEquals("Shelf", addresses.findById(other.id()).orElseThrow().name());
    }

    @Test
    void persistenceUniqueIndexRejectsMoveCollision() {
        AddressType type = createAddressType();
        Address sourceParent = addresses.insert(Address.create("Office", type.id(), null));
        Address targetParent = addresses.insert(Address.create("Bedroom", type.id(), null));
        Address moving = addresses.insert(Address.create("Drawer", type.id(), sourceParent.id()));
        addresses.insert(Address.create("drawer", type.id(), targetParent.id()));
        moving.applyAuthorizedParentChange(targetParent.id());

        assertThrows(AddressSiblingNameAlreadyExistsException.class, () -> addresses.update(moving));
        assertEquals(sourceParent.id(), addresses.findById(moving.id()).orElseThrow().parentId());
    }

    private AddressType createAddressType() {
        String code = "T" + UUID.randomUUID();
        return addressTypes.save(AddressType.create(code, "Test Type", null));
    }

    private void clearRows() {
        jpaAddresses.deleteAllInBatch();
        jpaAddressTypes.deleteAllInBatch();
    }

    private static void assertAddressState(
            Address address,
            UUID id,
            String name,
            String key,
            UUID addressTypeId,
            UUID parentId,
            boolean active,
            int version) {
        assertEquals(id, address.id());
        assertEquals(name, address.name());
        assertEquals(key, address.normalizedNameKey());
        assertEquals(addressTypeId, address.addressTypeId());
        assertEquals(parentId, address.parentId());
        assertEquals(active, address.active());
        assertEquals(version, address.version());
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
