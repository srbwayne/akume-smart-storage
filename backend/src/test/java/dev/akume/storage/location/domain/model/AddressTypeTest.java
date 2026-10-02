package dev.akume.storage.location.domain.model;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddressTypeTest {

    @Test
    void reconstitutesPersistedIdentityValuesAndInactiveState() {
        UUID id = UUID.randomUUID();

        AddressType addressType = AddressType.reconstitute(id, "RACK", "Prateleira", null, false);

        assertEquals(id, addressType.id());
        assertEquals("RACK", addressType.code());
        assertEquals("Prateleira", addressType.name());
        assertNull(addressType.description());
        assertFalse(addressType.active());
    }

    @Test
    void createsAddressTypeWithUuidAndProvidedValuesActiveByDefault() {
        AddressType addressType = AddressType.create("DRAWER", "Gaveta", "Small parts drawer");

        assertNotNull(addressType.id());
        assertEquals(4, addressType.id().version());
        assertEquals("DRAWER", addressType.code());
        assertEquals("Gaveta", addressType.name());
        assertEquals("Small parts drawer", addressType.description());
        assertTrue(addressType.active());
    }

    @Test
    void creationDoesNotNormalizeCodeOrName() {
        AddressType addressType = AddressType.create(" custom-code ", " Custom name ", null);

        assertEquals(" custom-code ", addressType.code());
        assertEquals(" Custom name ", addressType.name());
        assertNull(addressType.description());
    }

    @Test
    void rejectsNullCode() {
        assertThrows(IllegalArgumentException.class, () -> AddressType.create(null, "Gaveta", null));
    }

    @Test
    void rejectsBlankCode() {
        assertThrows(IllegalArgumentException.class, () -> AddressType.create(" \t ", "Gaveta", null));
    }

    @Test
    void rejectsNullName() {
        assertThrows(IllegalArgumentException.class, () -> AddressType.create("DRAWER", null, null));
    }

    @Test
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> AddressType.create("DRAWER", "\n ", null));
    }

    @Test
    void deactivatesAddressType() {
        AddressType addressType = AddressType.create("DRAWER", "Gaveta", null);

        addressType.deactivate();

        assertFalse(addressType.active());
    }

    @Test
    void activatesInactiveAddressType() {
        AddressType addressType = AddressType.create("DRAWER", "Gaveta", null);
        addressType.deactivate();

        addressType.activate();

        assertTrue(addressType.active());
    }

    @Test
    void activatingActiveAddressTypeLeavesItActive() {
        AddressType addressType = AddressType.create("DRAWER", "Gaveta", null);

        addressType.activate();

        assertTrue(addressType.active());
    }

    @Test
    void deactivatingInactiveAddressTypeLeavesItInactive() {
        AddressType addressType = AddressType.create("DRAWER", "Gaveta", null);
        addressType.deactivate();

        addressType.deactivate();

        assertFalse(addressType.active());
    }

    @Test
    void updatesDetailsWithoutChangingIdentityCodeOrActiveState() {
        UUID id = UUID.randomUUID();
        AddressType addressType = AddressType.reconstitute(id, "RACK", "Old name", "Old description", false);

        addressType.updateDetails("New name", "New description");

        assertEquals(id, addressType.id());
        assertEquals("RACK", addressType.code());
        assertEquals("New name", addressType.name());
        assertEquals("New description", addressType.description());
        assertFalse(addressType.active());
    }

    @Test
    void rejectsNullNameWhenUpdatingDetails() {
        AddressType addressType = AddressType.create("DRAWER", "Gaveta", null);

        assertThrows(IllegalArgumentException.class, () -> addressType.updateDetails(null, "description"));
    }

    @Test
    void rejectsBlankNameWhenUpdatingDetails() {
        AddressType addressType = AddressType.create("DRAWER", "Gaveta", null);

        assertThrows(IllegalArgumentException.class, () -> addressType.updateDetails(" \t ", "description"));
    }

    @Test
    void acceptsNullDescriptionWhenUpdatingDetails() {
        AddressType addressType = AddressType.create("DRAWER", "Gaveta", "Old description");

        addressType.updateDetails("New name", null);

        assertEquals("New name", addressType.name());
        assertNull(addressType.description());
    }
}
