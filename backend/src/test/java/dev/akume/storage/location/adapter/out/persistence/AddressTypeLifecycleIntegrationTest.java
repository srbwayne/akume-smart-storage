package dev.akume.storage.location.adapter.out.persistence;

import dev.akume.storage.location.application.exception.AddressTypeInUseException;
import dev.akume.storage.location.application.port.in.DeactivateAddressTypeUseCase;
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
class AddressTypeLifecycleIntegrationTest {

    @Autowired
    private AddressRepository addresses;

    @Autowired
    private AddressTypeRepository addressTypes;

    @Autowired
    private DeactivateAddressTypeUseCase deactivateAddressTypes;

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
    void activeUsageQueryDistinguishesTypesAndLifecycleStates() {
        AddressType targetType = createType();
        AddressType otherType = createType();

        assertFalse(addresses.existsActiveByAddressTypeId(targetType.id()));

        addresses.insert(address("Inactive target", targetType, false));
        assertFalse(addresses.existsActiveByAddressTypeId(targetType.id()));

        addresses.insert(address("Active other", otherType, true));
        assertFalse(addresses.existsActiveByAddressTypeId(targetType.id()));
        assertTrue(addresses.existsActiveByAddressTypeId(otherType.id()));

        addresses.insert(address("Active target", targetType, true));
        addresses.insert(address("Second inactive target", targetType, false));
        assertTrue(addresses.existsActiveByAddressTypeId(targetType.id()));
    }

    @Test
    void unusedActiveTypeCanBeDeactivated() {
        AddressType type = createType();

        AddressType result = deactivateAddressTypes.deactivate(type.id());

        assertFalse(result.active());
        assertFalse(addressTypes.findById(type.id()).orElseThrow().active());
    }

    @Test
    void inactiveAddressUsageDoesNotBlockTypeDeactivation() {
        AddressType type = createType();
        Address addressBefore = addresses.insert(address("Inactive user", type, false));

        AddressType result = deactivateAddressTypes.deactivate(type.id());

        AddressType typeAfter = addressTypes.findById(type.id()).orElseThrow();
        Address addressAfter = addresses.findById(addressBefore.id()).orElseThrow();
        assertFalse(result.active());
        assertFalse(typeAfter.active());
        assertEquals(addressBefore.id(), addressAfter.id());
        assertEquals(type.id(), addressAfter.addressTypeId());
        assertFalse(addressAfter.active());
        assertEquals(addressBefore.version(), addressAfter.version());
    }

    @Test
    void activeAddressUsageBlocksTypeDeactivationWithoutChangingEitherRow() {
        AddressType type = createType();
        Address addressBefore = addresses.insert(address("Active user", type, true));

        assertThrows(AddressTypeInUseException.class, () -> deactivateAddressTypes.deactivate(type.id()));

        AddressType typeAfter = addressTypes.findById(type.id()).orElseThrow();
        Address addressAfter = addresses.findById(addressBefore.id()).orElseThrow();
        assertTrue(typeAfter.active());
        assertEquals(addressBefore.id(), addressAfter.id());
        assertEquals(addressBefore.addressTypeId(), addressAfter.addressTypeId());
        assertTrue(addressAfter.active());
        assertEquals(addressBefore.version(), addressAfter.version());
    }

    @Test
    void repeatedDeactivationOfInactiveTypeRemainsSuccessful() {
        AddressType type = createType();
        deactivateAddressTypes.deactivate(type.id());

        AddressType result = deactivateAddressTypes.deactivate(type.id());

        assertFalse(result.active());
        assertFalse(addressTypes.findById(type.id()).orElseThrow().active());
    }

    private AddressType createType() {
        String suffix = UUID.randomUUID().toString();
        return addressTypes.save(AddressType.create("TYPE_" + suffix, "Type " + suffix, null));
    }

    private static Address address(String label, AddressType type, boolean active) {
        Address address = Address.create(label + " " + UUID.randomUUID(), type.id(), null);
        if (!active) {
            address.deactivate();
        }
        return address;
    }

    private void clearRows() {
        jdbc.update("DELETE FROM addresses");
        jdbc.update("DELETE FROM address_types");
    }
}
