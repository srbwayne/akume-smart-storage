package dev.akume.storage.location.adapter.out.persistence;

import dev.akume.storage.location.application.exception.AddressCycleDetectedException;
import dev.akume.storage.location.application.exception.InactiveAddressParentException;
import dev.akume.storage.location.application.port.in.CreateAddressCommand;
import dev.akume.storage.location.application.port.in.CreateAddressUseCase;
import dev.akume.storage.location.application.port.in.MoveAddressCommand;
import dev.akume.storage.location.application.port.in.MoveAddressUseCase;
import dev.akume.storage.location.application.port.out.AddressRepository;
import dev.akume.storage.location.application.port.out.AddressTypeRepository;
import dev.akume.storage.location.domain.exception.AddressSiblingNameAlreadyExistsException;
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
class AddressMoveIntegrationTest {

    @Autowired
    private MoveAddressUseCase moveAddresses;

    @Autowired
    private CreateAddressUseCase createAddresses;

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
    void movesParentAndPreservesItsChildAndPersistedState() {
        AddressType type = createType();
        Address oldParent = create("Old parent", type, null);
        Address source = create("Shelf", type, oldParent.id());
        Address child = create("Drawer", type, source.id());
        Address newParent = create("New parent", type, null);
        Address before = reload(source.id());

        Address result = moveAddresses.move(
                new MoveAddressCommand(source.id(), newParent.id(), before.version()));
        Address moved = reload(source.id());
        Address preservedChild = reload(child.id());

        assertEquals(newParent.id(), result.parentId());
        assertEquals(newParent.id(), moved.parentId());
        assertEquals(before.version() + 1, result.version());
        assertEquals(before.version() + 1, moved.version());
        assertEquals(before.id(), moved.id());
        assertEquals(before.name(), moved.name());
        assertEquals(before.normalizedNameKey(), moved.normalizedNameKey());
        assertEquals(before.addressTypeId(), moved.addressTypeId());
        assertEquals(before.active(), moved.active());
        assertEquals(source.id(), preservedChild.parentId());
    }

    @Test
    void movesChildToRootAndIncrementsVersion() {
        AddressType type = createType();
        Address parent = create("Parent", type, null);
        Address child = create("Child", type, parent.id());
        Address before = reload(child.id());

        Address result = moveAddresses.move(new MoveAddressCommand(child.id(), null, before.version()));
        Address moved = reload(child.id());

        assertEquals(null, result.parentId());
        assertEquals(null, moved.parentId());
        assertEquals(before.version() + 1, result.version());
        assertEquals(before.version() + 1, moved.version());
    }

    @Test
    void sameParentMoveIsNoOpWithoutVersionIncrement() {
        AddressType type = createType();
        Address parent = create("Parent", type, null);
        Address child = create("Child", type, parent.id());
        Address before = reload(child.id());

        Address result = moveAddresses.move(
                new MoveAddressCommand(child.id(), parent.id(), before.version()));
        Address after = reload(child.id());

        assertEquals(parent.id(), result.parentId());
        assertEquals(before.parentId(), after.parentId());
        assertEquals(before.version(), result.version());
        assertEquals(before.version(), after.version());
    }

    @Test
    void rootToRootMoveIsNoOpWithoutVersionIncrement() {
        AddressType type = createType();
        Address root = create("Root", type, null);
        Address before = reload(root.id());

        Address result = moveAddresses.move(new MoveAddressCommand(root.id(), null, before.version()));
        Address after = reload(root.id());

        assertEquals(null, result.parentId());
        assertEquals(null, after.parentId());
        assertEquals(before.version(), result.version());
        assertEquals(before.version(), after.version());
    }

    @Test
    void rejectsDeepCycleWithoutChangingPersistedHierarchy() {
        AddressType type = createType();
        Address first = create("First", type, null);
        Address second = create("Second", type, first.id());
        Address third = create("Third", type, second.id());
        Address before = reload(first.id());

        assertThrows(AddressCycleDetectedException.class,
                () -> moveAddresses.move(new MoveAddressCommand(first.id(), third.id(), before.version())));

        assertEquals(before.parentId(), reload(first.id()).parentId());
        assertEquals(first.id(), reload(second.id()).parentId());
        assertEquals(second.id(), reload(third.id()).parentId());
        assertEquals(before.version(), reload(first.id()).version());
    }

    @Test
    void inactiveSourceMayMoveUnderInactiveDestinationAndRemainsInactive() {
        AddressType type = createType();
        Address activeParent = create("Active parent", type, null);
        Address createdSource = create("Inactive source", type, activeParent.id());
        Address source = addresses.update(deactivate(createdSource));
        Address destination = insertInactive("Inactive destination", type, null);
        Address before = reload(source.id());
        Address destinationBeforeMove = reload(destination.id());

        assertFalse(before.active());
        assertFalse(destinationBeforeMove.active());

        Address result = moveAddresses.move(
                new MoveAddressCommand(source.id(), destination.id(), before.version()));
        Address moved = reload(source.id());

        assertEquals(destination.id(), moved.parentId());
        assertFalse(moved.active());
        assertFalse(result.active());
        assertEquals(before.version() + 1, moved.version());
        assertEquals(before.version() + 1, result.version());
    }

    @Test
    void activeSourceCannotMoveUnderInactiveDestination() {
        AddressType type = createType();
        Address activeParent = create("Active parent", type, null);
        Address source = create("Active source", type, activeParent.id());
        Address destination = insertInactive("Inactive destination", type, null);
        Address before = reload(source.id());

        assertThrows(InactiveAddressParentException.class,
                () -> moveAddresses.move(
                        new MoveAddressCommand(source.id(), destination.id(), before.version())));

        Address after = reload(source.id());
        assertEquals(before.parentId(), after.parentId());
        assertEquals(before.version(), after.version());
        assertEquals(before.active(), after.active());
    }

    @Test
    void destinationSiblingDuplicateRejectsMoveWithoutChangingSource() {
        AddressType type = createType();
        Address oldParent = create("Old parent", type, null);
        Address source = create("Drawer", type, oldParent.id());
        Address destination = create("Destination", type, null);
        create("drawer", type, destination.id());
        Address before = reload(source.id());

        assertThrows(AddressSiblingNameAlreadyExistsException.class,
                () -> moveAddresses.move(
                        new MoveAddressCommand(source.id(), destination.id(), before.version())));

        Address after = reload(source.id());
        assertEquals(before.parentId(), after.parentId());
        assertEquals(before.version(), after.version());
    }

    private Address create(String name, AddressType type, UUID parentId) {
        return createAddresses.create(new CreateAddressCommand(name, type.id(), parentId));
    }

    private Address insertInactive(String name, AddressType type, UUID parentId) {
        Address address = Address.create(name, type.id(), parentId);
        address.deactivate();
        return addresses.insert(address);
    }

    private Address deactivate(Address address) {
        address.deactivate();
        return address;
    }

    private Address reload(UUID id) {
        return addresses.findById(id).orElseThrow();
    }

    private AddressType createType() {
        String code = "M2C-" + UUID.randomUUID().toString().substring(0, 8);
        return addressTypes.save(AddressType.create(code, "Type " + code, null));
    }

    private void clearRows() {
        jdbc.update("DELETE FROM addresses");
        jdbc.update("DELETE FROM address_types");
    }
}
