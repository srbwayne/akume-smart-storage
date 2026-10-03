package dev.akume.storage.location.adapter.out.persistence;

import dev.akume.storage.location.application.port.in.CreateAddressCommand;
import dev.akume.storage.location.application.port.in.CreateAddressUseCase;
import dev.akume.storage.location.application.port.in.RenameAddressCommand;
import dev.akume.storage.location.application.port.in.RenameAddressUseCase;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AddressCreateRenameIntegrationTest {

    @Autowired
    private CreateAddressUseCase createAddresses;

    @Autowired
    private RenameAddressUseCase renameAddresses;

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
    void createsRootAndChildWithPersistedDomainState() {
        AddressType type = createType();

        Address root = createAddresses.create(new CreateAddressCommand(" Home ", type.id(), null));
        Address child = createAddresses.create(new CreateAddressCommand("Drawer", type.id(), root.id()));

        Address storedRoot = addresses.findById(root.id()).orElseThrow();
        Address storedChild = addresses.findById(child.id()).orElseThrow();
        assertEquals("Home", storedRoot.name());
        assertEquals("home", storedRoot.normalizedNameKey());
        assertEquals(type.id(), storedRoot.addressTypeId());
        assertEquals(null, storedRoot.parentId());
        assertTrue(storedRoot.active());
        assertEquals(root.id(), storedChild.parentId());
        assertEquals(type.id(), storedChild.addressTypeId());
        assertEquals("drawer", storedChild.normalizedNameKey());
        assertTrue(storedChild.active());
        assertEquals(0, storedRoot.version());
        assertEquals(0, storedChild.version());
    }

    @Test
    void createRejectsDuplicateRootAndChildNamesAcrossCanonicalCase() {
        AddressType type = createType();
        createAddresses.create(new CreateAddressCommand("Home", type.id(), null));

        assertThrows(AddressSiblingNameAlreadyExistsException.class,
                () -> createAddresses.create(new CreateAddressCommand("HOME", type.id(), null)));

        Address parent = createAddresses.create(new CreateAddressCommand("Office", type.id(), null));
        createAddresses.create(new CreateAddressCommand("Drawer", type.id(), parent.id()));
        assertThrows(AddressSiblingNameAlreadyExistsException.class,
                () -> createAddresses.create(new CreateAddressCommand("DRAWER", type.id(), parent.id())));
    }

    @Test
    void renamePersistsNameKeyAndVersionWithoutChangingStructuralOrLifecycleState() {
        AddressType type = createType();
        Address parent = createAddresses.create(new CreateAddressCommand("Office", type.id(), null));
        Address child = createAddresses.create(new CreateAddressCommand("Old drawer", type.id(), parent.id()));

        Address renamed = renameAddresses.rename(
                new RenameAddressCommand(child.id(), "  Main Drawer  ", child.version()));
        Address stored = addresses.findById(child.id()).orElseThrow();
        int databaseVersion = jdbc.queryForObject(
                "SELECT version FROM addresses WHERE id = ?", Integer.class, child.id());

        assertEquals("Main Drawer", renamed.name());
        assertEquals("main drawer", renamed.normalizedNameKey());
        assertEquals(child.version() + 1, renamed.version());
        assertEquals(renamed.version(), databaseVersion);
        assertEquals(child.id(), stored.id());
        assertEquals(type.id(), stored.addressTypeId());
        assertEquals(parent.id(), stored.parentId());
        assertTrue(stored.active());
        assertEquals("Main Drawer", stored.name());
        assertEquals("main drawer", stored.normalizedNameKey());
        assertEquals(parent.id(), stored.parentId());
    }

    @Test
    void caseOnlyRenamePersistsDisplayChangeAndIncrementsVersionWithoutChangingStructure() {
        AddressType type = createType();
        Address parent = createAddresses.create(new CreateAddressCommand("Office", type.id(), null));
        Address created = createAddresses.create(new CreateAddressCommand("Drawer", type.id(), parent.id()));
        Address before = addresses.findById(created.id()).orElseThrow();

        Address renamed = renameAddresses.rename(
                new RenameAddressCommand(before.id(), "DRAWER", before.version()));
        Address persisted = addresses.findById(before.id()).orElseThrow();

        assertEquals("DRAWER", renamed.name());
        assertEquals("DRAWER", persisted.name());
        assertEquals(before.normalizedNameKey(), renamed.normalizedNameKey());
        assertEquals(before.normalizedNameKey(), persisted.normalizedNameKey());
        assertEquals(before.version() + 1, renamed.version());
        assertEquals(before.version() + 1, persisted.version());
        assertEquals(before.id(), persisted.id());
        assertEquals(before.addressTypeId(), persisted.addressTypeId());
        assertEquals(before.parentId(), persisted.parentId());
        assertEquals(before.active(), persisted.active());
    }

    @Test
    void renameRejectsDuplicateRootAndChildNames() {
        AddressType type = createType();
        Address rootA = createAddresses.create(new CreateAddressCommand("Alpha", type.id(), null));
        Address rootB = createAddresses.create(new CreateAddressCommand("Beta", type.id(), null));
        assertThrows(AddressSiblingNameAlreadyExistsException.class,
                () -> renameAddresses.rename(new RenameAddressCommand(rootB.id(), "ALPHA", rootB.version())));

        Address parent = createAddresses.create(new CreateAddressCommand("Office", type.id(), null));
        Address childA = createAddresses.create(new CreateAddressCommand("Drawer A", type.id(), parent.id()));
        Address childB = createAddresses.create(new CreateAddressCommand("Drawer B", type.id(), parent.id()));
        assertThrows(AddressSiblingNameAlreadyExistsException.class,
                () -> renameAddresses.rename(
                        new RenameAddressCommand(childB.id(), childA.name(), childB.version())));

        assertEquals("Beta", addresses.findById(rootB.id()).orElseThrow().name());
        assertEquals("Drawer B", addresses.findById(childB.id()).orElseThrow().name());
    }

    @Test
    void identicalDisplayNameStillUsesRenameUpdatePathAndKeepsPersistedVersion() {
        AddressType type = createType();
        Address address = createAddresses.create(new CreateAddressCommand("Room", type.id(), null));

        Address result = renameAddresses.rename(
                new RenameAddressCommand(address.id(), address.name(), address.version()));

        assertEquals(address.name(), result.name());
        assertEquals(address.normalizedNameKey(), result.normalizedNameKey());
        assertEquals(address.version(), result.version());
        assertEquals(address.version(), addresses.findById(address.id()).orElseThrow().version());
    }

    private AddressType createType() {
        String code = "M2B-" + UUID.randomUUID().toString().substring(0, 8);
        return addressTypes.save(AddressType.create(code, "Type " + code, null));
    }

    private void clearRows() {
        jdbc.update("DELETE FROM addresses");
        jdbc.update("DELETE FROM address_types");
    }
}
