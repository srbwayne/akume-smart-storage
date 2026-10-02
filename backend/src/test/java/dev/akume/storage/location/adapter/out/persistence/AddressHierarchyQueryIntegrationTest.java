package dev.akume.storage.location.adapter.out.persistence;

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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AddressHierarchyQueryIntegrationTest {

    @Autowired
    private AddressRepository addresses;

    @Autowired
    private SpringDataAddressRepository jpaAddresses;

    @Autowired
    private AddressTypeRepository addressTypes;

    @Autowired
    private SpringDataAddressTypeRepository jpaAddressTypes;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearExistingRows() {
        clearRows();
    }

    @AfterEach
    void clearTestRows() {
        clearRows();
    }

    @Test
    void findAllReturnsFlatRowsExactlyOnceInIdOrder() {
        AddressType type = createAddressType();
        Address root = insert("Home", type, null, true);
        Address child = insert("Office", type, root.id(), true);
        Address inactiveRoot = insert("Storage", type, null, false);

        List<Address> found = addresses.findAll();
        List<UUID> expectedIdOrder = jdbc.query(
                "SELECT id FROM addresses ORDER BY id",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class));

        assertEquals(3, found.size());
        assertEquals(expectedIdOrder, found.stream().map(Address::id).toList());
        assertEquals(Set.of(root.id(), child.id(), inactiveRoot.id()),
                found.stream().map(Address::id).collect(java.util.stream.Collectors.toSet()));
        assertTrue(found.stream().anyMatch(address -> address.id().equals(child.id())
                && address.parentId().equals(root.id())));
        assertTrue(found.stream().anyMatch(address -> address.id().equals(inactiveRoot.id())
                && !address.active()));
    }

    @Test
    void findRootsReturnsOnlyRootsIncludingInactiveInCanonicalOrder() {
        AddressType type = createAddressType();
        Address zulu = insert("Zulu", type, null, true);
        Address alpha = insert("Alpha", type, null, true);
        Address betaInactive = insert("Beta", type, null, false);
        Address child = insert("Child", type, alpha.id(), true);

        List<Address> roots = addresses.findRoots();

        assertEquals(List.of(alpha.id(), betaInactive.id(), zulu.id()),
                roots.stream().map(Address::id).toList());
        assertFalse(roots.stream().anyMatch(address -> address.id().equals(child.id())));
        assertFalse(roots.get(1).active());
    }

    @Test
    void findDirectChildrenOrdersChildrenAndExcludesGrandchildren() {
        AddressType type = createAddressType();
        Address parent = insert("Room", type, null, true);
        Address childB = insert("Child B", type, parent.id(), true);
        Address childA = insert("Child A", type, parent.id(), true);
        Address childCInactive = insert("Child C", type, parent.id(), false);
        Address grandchild = insert("Grandchild", type, childA.id(), true);

        List<Address> children = addresses.findDirectChildren(parent.id());

        assertEquals(List.of(childA.id(), childB.id(), childCInactive.id()),
                children.stream().map(Address::id).toList());
        assertFalse(children.stream().anyMatch(address -> address.id().equals(grandchild.id())));
        assertFalse(children.get(2).active());
        assertTrue(addresses.findDirectChildren(UUID.randomUUID()).isEmpty());
    }

    @Test
    void rootSiblingExistenceHonorsExclusionAndIncludesInactiveRoots() {
        AddressType type = createAddressType();
        Address inactiveRoot = insert("Home", type, null, false);

        assertTrue(addresses.existsRootNameKey("home", UUID.randomUUID()));
        assertFalse(addresses.existsRootNameKey("home", inactiveRoot.id()));
        assertFalse(addresses.existsRootNameKey("missing", UUID.randomUUID()));
    }

    @Test
    void rootSiblingExistenceRejectsNullExcludedAddressId() {
        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> addresses.existsRootNameKey("home", null));

        assertEquals("excludedAddressId must not be null", failure.getMessage());
    }

    @Test
    void childSiblingExistenceHonorsParentExclusionAndInactiveRows() {
        AddressType type = createAddressType();
        Address firstParent = insert("Office", type, null, true);
        Address secondParent = insert("Bedroom", type, null, true);
        Address inactiveChild = insert("Drawer", type, firstParent.id(), false);
        insert("drawer", type, secondParent.id(), true);

        assertTrue(addresses.existsChildNameKey(firstParent.id(), "drawer", UUID.randomUUID()));
        assertFalse(addresses.existsChildNameKey(firstParent.id(), "drawer", inactiveChild.id()));
        assertTrue(addresses.existsChildNameKey(secondParent.id(), "drawer", UUID.randomUUID()));
        assertFalse(addresses.existsChildNameKey(firstParent.id(), "other", UUID.randomUUID()));
    }

    @Test
    void childSiblingExistenceRejectsNullExcludedAddressId() {
        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> addresses.existsChildNameKey(UUID.randomUUID(), "drawer", null));

        assertEquals("excludedAddressId must not be null", failure.getMessage());
    }

    @Test
    void activeChildQueryOnlyExaminesDirectActiveChildren() {
        AddressType type = createAddressType();
        Address withActiveChild = insert("Active parent", type, null, true);
        insert("Active child", type, withActiveChild.id(), true);

        Address withInactiveChild = insert("Inactive parent", type, null, true);
        Address inactiveChild = insert("Inactive child", type, withInactiveChild.id(), false);
        insert("Active grandchild", type, inactiveChild.id(), true);

        Address empty = insert("Empty parent", type, null, true);

        assertTrue(addresses.hasActiveDirectChild(withActiveChild.id()));
        assertFalse(addresses.hasActiveDirectChild(withInactiveChild.id()));
        assertFalse(addresses.hasActiveDirectChild(empty.id()));
    }

    @Test
    void descendantQueryTraversesDownwardAndUsesStrictSemantics() {
        AddressType type = createAddressType();
        Address root = insert("Root", type, null, true);
        Address child = insert("Child", type, root.id(), true);
        Address grandchild = insert("Grandchild", type, child.id(), true);
        Address unrelated = insert("Unrelated", type, null, true);

        assertTrue(addresses.isDescendant(root.id(), child.id()));
        assertTrue(addresses.isDescendant(root.id(), grandchild.id()));
        assertFalse(addresses.isDescendant(grandchild.id(), root.id()));
        assertFalse(addresses.isDescendant(root.id(), unrelated.id()));
        assertFalse(addresses.isDescendant(root.id(), root.id()));
    }

    @Test
    void descendantQueryTerminatesOnCorruptedCycleAndKeepsSelfStrict() {
        AddressType type = createAddressType();
        Address first = insert("First", type, null, true);
        Address second = insert("Second", type, first.id(), true);

        // Controlled historical-corruption fixture: normal FK and self-parent constraints remain enabled.
        jdbc.update("UPDATE addresses SET parent_id = ? WHERE id = ?", second.id(), first.id());

        assertFalse(addresses.isDescendant(first.id(), first.id()));
        assertFalse(addresses.isDescendant(second.id(), second.id()));
        assertTrue(addresses.isDescendant(first.id(), second.id()));
        assertTrue(addresses.isDescendant(second.id(), first.id()));
    }

    private Address insert(String name, AddressType type, UUID parentId, boolean active) {
        Address address = Address.create(name, type.id(), parentId);
        if (!active) {
            address.deactivate();
        }
        return addresses.insert(address);
    }

    private AddressType createAddressType() {
        String code = "T" + UUID.randomUUID();
        return addressTypes.save(AddressType.create(code, "Test Type", null));
    }

    private void clearRows() {
        jpaAddresses.deleteAllInBatch();
        jpaAddressTypes.deleteAllInBatch();
    }
}
