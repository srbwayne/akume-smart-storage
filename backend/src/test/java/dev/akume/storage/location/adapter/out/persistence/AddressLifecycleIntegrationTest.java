package dev.akume.storage.location.adapter.out.persistence;

import dev.akume.storage.location.application.exception.AddressHasActiveChildrenException;
import dev.akume.storage.location.application.exception.InactiveAddressParentException;
import dev.akume.storage.location.application.exception.InactiveAddressTypeException;
import dev.akume.storage.location.application.port.in.ActivateAddressCommand;
import dev.akume.storage.location.application.port.in.ActivateAddressUseCase;
import dev.akume.storage.location.application.port.in.DeactivateAddressCommand;
import dev.akume.storage.location.application.port.in.DeactivateAddressUseCase;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.domain.model.Address;
import dev.akume.storage.location.domain.model.AddressType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AddressLifecycleIntegrationTest {

    @Autowired
    private ActivateAddressUseCase activateAddresses;

    @Autowired
    private DeactivateAddressUseCase deactivateAddresses;

    @Autowired
    private AddressRepository addresses;

    @Autowired
    private AddressTypeRepository addressTypes;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearBeforeTest() {
        clearRows();
    }

    @AfterEach
    void clearAfterTest() {
        clearRows();
    }

    @Test
    void activatesInactiveRootAndIncrementsPersistedVersion() {
        AddressType type = createType();
        Address created = create("Root", type, null);
        Address inactive = deactivatePersisted(created);
        Address before = reload(inactive.id());

        Address result = activateAddresses.activate(new ActivateAddressCommand(before.id(), before.version()));
        Address after = reload(before.id());

        assertTrue(after.active());
        assertTrue(result.active());
        assertEquals(before.version() + 1, after.version());
        assertEquals(after.version(), result.version());
        assertEquals(before.parentId(), after.parentId());
        assertEquals(before.addressTypeId(), after.addressTypeId());
    }

    @Test
    void activatingAlreadyActiveRootIsNoOp() {
        AddressType type = createType();
        Address before = reload(create("Root", type, null).id());

        Address result = activateAddresses.activate(new ActivateAddressCommand(before.id(), before.version()));
        Address after = reload(before.id());

        assertTrue(after.active());
        assertEquals(before.version(), result.version());
        assertEquals(before.version(), after.version());
    }

    @Test
    void activatesInactiveChildWithActiveParent() {
        AddressType type = createType();
        Address parent = create("Parent", type, null);
        Address child = deactivatePersisted(create("Child", type, parent.id()));
        Address before = reload(child.id());

        Address result = activateAddresses.activate(new ActivateAddressCommand(before.id(), before.version()));
        Address after = reload(before.id());

        assertTrue(after.active());
        assertEquals(before.version() + 1, after.version());
        assertEquals(before.parentId(), after.parentId());
        assertEquals(result.version(), after.version());
    }

    @Test
    void activatingAlreadyActiveChildWithValidParentIsNoOp() {
        AddressType type = createType();
        Address parent = create("Parent", type, null);
        Address before = reload(create("Child", type, parent.id()).id());

        Address result = activateAddresses.activate(new ActivateAddressCommand(before.id(), before.version()));
        Address after = reload(before.id());

        assertTrue(after.active());
        assertEquals(parent.id(), after.parentId());
        assertEquals(before.version(), result.version());
        assertEquals(before.version(), after.version());
    }

    @Test
    void inactiveTypeRejectsBothInactiveAndAlreadyActiveAddresses() {
        AddressType type = createType();
        Address alreadyActive = create("Active", type, null);
        Address inactive = deactivatePersisted(create("Inactive", type, null));

        AddressType inactiveType = addressTypes.findById(type.id()).orElseThrow();
        inactiveType.deactivate();
        addressTypes.save(inactiveType);

        AddressType typeBeforeInactiveAttempt = addressTypes.findById(type.id()).orElseThrow();
        Address inactiveAddressBefore = reload(inactive.id());
        assertFalse(typeBeforeInactiveAttempt.active());
        assertFalse(inactiveAddressBefore.active());

        assertThrows(InactiveAddressTypeException.class,
                () -> activateAddresses.activate(
                        new ActivateAddressCommand(inactiveAddressBefore.id(), inactiveAddressBefore.version())));

        Address activeAddressBefore = reload(alreadyActive.id());
        AddressType typeBeforeActiveAttempt = addressTypes.findById(type.id()).orElseThrow();
        assertTrue(activeAddressBefore.active());
        assertFalse(typeBeforeActiveAttempt.active());
        assertThrows(InactiveAddressTypeException.class,
                () -> activateAddresses.activate(
                        new ActivateAddressCommand(activeAddressBefore.id(), activeAddressBefore.version())));

        assertFalse(reload(inactive.id()).active());
        assertTrue(reload(alreadyActive.id()).active());
        assertEquals(inactiveAddressBefore.version(), reload(inactive.id()).version());
        assertEquals(activeAddressBefore.version(), reload(alreadyActive.id()).version());
    }

    @Test
    void inactiveParentRejectsInactiveAndAlreadyActiveChildren() {
        AddressType type = createType();
        Address parent = create("Parent", type, null);
        Address inactiveChild = deactivatePersisted(create("Inactive child", type, parent.id()));
        Address activeChild = create("Active child", type, parent.id());

        deactivatePersisted(parent);
        Address parentBeforeInactiveChildAttempt = reload(parent.id());
        assertFalse(parentBeforeInactiveChildAttempt.active());
        Address inactiveChildBefore = reload(inactiveChild.id());
        assertFalse(inactiveChildBefore.active());
        assertEquals(parentBeforeInactiveChildAttempt.id(), inactiveChildBefore.parentId());

        assertThrows(InactiveAddressParentException.class,
                () -> activateAddresses.activate(new ActivateAddressCommand(
                        inactiveChildBefore.id(), inactiveChildBefore.version())));

        Address parentBeforeActiveChildAttempt = reload(parent.id());
        Address activeChildBefore = reload(activeChild.id());
        assertFalse(parentBeforeActiveChildAttempt.active());
        assertTrue(activeChildBefore.active());
        assertEquals(parentBeforeActiveChildAttempt.id(), activeChildBefore.parentId());
        assertThrows(InactiveAddressParentException.class,
                () -> activateAddresses.activate(new ActivateAddressCommand(
                        activeChildBefore.id(), activeChildBefore.version())));

        assertFalse(reload(inactiveChild.id()).active());
        assertTrue(reload(activeChild.id()).active());
        assertEquals(inactiveChildBefore.version(), reload(inactiveChild.id()).version());
        assertEquals(activeChildBefore.version(), reload(activeChild.id()).version());
    }

    @Test
    void deactivatesActiveLeafAndPreservesStructure() {
        AddressType type = createType();
        Address parent = create("Parent", type, null);
        Address before = reload(create("Leaf", type, parent.id()).id());

        Address result = deactivateAddresses.deactivate(new DeactivateAddressCommand(before.id(), before.version()));
        Address after = reload(before.id());

        assertFalse(after.active());
        assertEquals(before.version() + 1, after.version());
        assertEquals(result.version(), after.version());
        assertEquals(before.id(), after.id());
        assertEquals(before.name(), after.name());
        assertEquals(before.normalizedNameKey(), after.normalizedNameKey());
        assertEquals(before.addressTypeId(), after.addressTypeId());
        assertEquals(before.parentId(), after.parentId());
    }

    @Test
    void deactivatingAlreadyInactiveAddressIsNoOp() {
        AddressType type = createType();
        Address before = reload(deactivatePersisted(create("Inactive", type, null)).id());

        Address result = deactivateAddresses.deactivate(new DeactivateAddressCommand(before.id(), before.version()));
        Address after = reload(before.id());

        assertFalse(after.active());
        assertEquals(before.version(), result.version());
        assertEquals(before.version(), after.version());
    }

    @Test
    void activeDirectChildBlocksDeactivation() {
        AddressType type = createType();
        Address parent = create("Parent", type, null);
        Address child = create("Active child", type, parent.id());
        Address parentBefore = reload(parent.id());
        Address childBefore = reload(child.id());
        assertTrue(parentBefore.active());
        assertTrue(childBefore.active());
        assertEquals(parentBefore.id(), childBefore.parentId());

        assertThrows(AddressHasActiveChildrenException.class,
                () -> deactivateAddresses.deactivate(
                        new DeactivateAddressCommand(parentBefore.id(), parentBefore.version())));

        Address parentAfter = reload(parent.id());
        Address childAfter = reload(child.id());
        assertTrue(parentAfter.active());
        assertEquals(parentBefore.version(), parentAfter.version());
        assertTrue(childAfter.active());
        assertEquals(parentBefore.id(), childAfter.parentId());
    }

    @Test
    void inactiveDirectChildDoesNotBlockParentDeactivation() {
        AddressType type = createType();
        Address parent = create("Parent", type, null);
        Address child = insertInactive("Inactive child", type, parent.id());
        Address parentBefore = reload(parent.id());
        Address childBefore = reload(child.id());
        assertTrue(parentBefore.active());
        assertFalse(childBefore.active());
        assertEquals(parentBefore.id(), childBefore.parentId());

        Address result = deactivateAddresses.deactivate(
                new DeactivateAddressCommand(parentBefore.id(), parentBefore.version()));
        Address after = reload(parent.id());
        Address childAfter = reload(child.id());

        assertFalse(after.active());
        assertEquals(parentBefore.version() + 1, after.version());
        assertEquals(result.version(), after.version());
        assertFalse(childAfter.active());
        assertEquals(parent.id(), childAfter.parentId());
    }

    private Address create(String name, AddressType type, UUID parentId) {
        return addresses.insert(Address.create(name, type.id(), parentId));
    }

    private Address insertInactive(String name, AddressType type, UUID parentId) {
        Address address = Address.create(name, type.id(), parentId);
        address.deactivate();
        return addresses.insert(address);
    }

    private Address deactivatePersisted(Address address) {
        address.deactivate();
        return addresses.update(address);
    }

    private Address reload(UUID id) {
        return addresses.findById(id).orElseThrow();
    }

    private AddressType createType() {
        String code = "M2D-" + UUID.randomUUID().toString().substring(0, 8);
        return addressTypes.save(AddressType.create(code, "Type " + code, null));
    }

    private void clearRows() {
        jdbc.update("DELETE FROM addresses");
        jdbc.update("DELETE FROM address_types");
    }
}
