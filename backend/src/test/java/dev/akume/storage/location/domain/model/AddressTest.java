package dev.akume.storage.location.domain.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddressTest {

    private static final UUID TYPE_ID = UUID.randomUUID();

    @Test
    void createsUuidActiveRootWithAddressTypeAndStrippedDisplayName() {
        Address address = Address.create("  Sala Principal  ", TYPE_ID, null);

        assertNotNull(address.id());
        assertEquals(4, address.id().version());
        assertEquals("Sala Principal", address.name());
        assertEquals("sala principal", address.normalizedNameKey());
        assertEquals(TYPE_ID, address.addressTypeId());
        assertNull(address.parentId());
        assertTrue(address.active());
        assertEquals(0, address.version());
    }

    @Test
    void createsChildWithProvidedParent() {
        UUID parentId = UUID.randomUUID();

        Address address = Address.create("Gaveta 01", TYPE_ID, parentId);

        assertEquals(parentId, address.parentId());
    }

    @Test
    void rejectsNullAddressTypeId() {
        assertThrows(IllegalArgumentException.class, () -> Address.create("Sala", null, null));
    }

    @Test
    void rejectsNullName() {
        assertThrows(IllegalArgumentException.class, () -> Address.create(null, TYPE_ID, null));
    }

    @Test
    void rejectsBlankAndWhitespaceOnlyNames() {
        assertThrows(IllegalArgumentException.class, () -> Address.create("", TYPE_ID, null));
        assertThrows(IllegalArgumentException.class, () -> Address.create(" \t\n ", TYPE_ID, null));
    }

    @Test
    void stripsOuterWhitespaceButPreservesDisplayCaseAccentsAndInternalWhitespace() {
        Address address = Address.create("  Sala  Principal Á  ", TYPE_ID, null);

        assertEquals("Sala  Principal Á", address.name());
        assertEquals("sala  principal á", address.normalizedNameKey());
    }

    @Test
    void canonicalKeyIgnoresCaseAndOuterWhitespace() {
        Address spaced = Address.create("  Drawer  ", TYPE_ID, null);
        Address lower = Address.create("drawer", TYPE_ID, null);

        assertEquals(spaced.normalizedNameKey(), lower.normalizedNameKey());
    }

    @Test
    void canonicalKeyDoesNotDependOnDefaultLocale() {
        Locale originalLocale = Locale.getDefault();

        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            Address address = Address.create("ISLAND", TYPE_ID, null);

            assertEquals("island", address.normalizedNameKey());
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void canonicalKeyNormalizesComposedAndDecomposedUnicodeToNfc() {
        String composed = "Café";
        String decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD);

        Address composedAddress = Address.create(composed, TYPE_ID, null);
        Address decomposedAddress = Address.create(decomposed, TYPE_ID, null);

        assertEquals(composedAddress.normalizedNameKey(), decomposedAddress.normalizedNameKey());
        assertEquals(decomposed, decomposedAddress.name());
    }

    @Test
    void canonicalKeyPreservesAccentDistinction() {
        Address accented = Address.create("Armário", TYPE_ID, null);
        Address unaccented = Address.create("armario", TYPE_ID, null);

        assertNotEquals(accented.normalizedNameKey(), unaccented.normalizedNameKey());
    }

    @Test
    void canonicalKeyPreservesInternalWhitespaceDistinction() {
        Address doubled = Address.create("Sala  Principal", TYPE_ID, null);
        Address single = Address.create("Sala Principal", TYPE_ID, null);

        assertNotEquals(doubled.normalizedNameKey(), single.normalizedNameKey());
    }

    @Test
    void reconstitutesPersistedStateWithoutChangingValues() {
        UUID id = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        String name = "Drawer";

        Address address = Address.reconstitute(
                id, name, "drawer", TYPE_ID, parentId, false, 17);

        assertEquals(id, address.id());
        assertEquals(name, address.name());
        assertEquals("drawer", address.normalizedNameKey());
        assertEquals(TYPE_ID, address.addressTypeId());
        assertEquals(parentId, address.parentId());
        assertFalse(address.active());
        assertEquals(17, address.version());
    }

    @Test
    void reconstitutesConsistentCanonicalKey() {
        Address address = Address.reconstitute(
                UUID.randomUUID(), "  Sala  ".strip(), "sala", TYPE_ID, null, true, 0);

        assertEquals("sala", address.normalizedNameKey());
    }

    @Test
    void rejectsInconsistentPersistedNameAndKeyInsteadOfRepairingIt() {
        assertThrows(IllegalArgumentException.class, () -> Address.reconstitute(
                UUID.randomUUID(), "Drawer", "not-drawer", TYPE_ID, null, true, 0));
    }

    @Test
    void rejectsPersistedDisplayNameThatWasNotStripped() {
        assertThrows(IllegalArgumentException.class, () -> Address.reconstitute(
                UUID.randomUUID(), " Drawer ", "drawer", TYPE_ID, null, true, 0));
    }

    @Test
    void rejectsInvalidPersistedIdentityTypeParentAndVersion() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> Address.reconstitute(
                null, "Shelf", "shelf", TYPE_ID, null, true, 0));
        assertThrows(IllegalArgumentException.class, () -> Address.reconstitute(
                id, "Shelf", "shelf", null, null, true, 0));
        assertThrows(IllegalArgumentException.class, () -> Address.reconstitute(
                id, "Shelf", "shelf", TYPE_ID, id, true, 0));
        assertThrows(IllegalArgumentException.class, () -> Address.reconstitute(
                id, "Shelf", "shelf", TYPE_ID, null, true, -1));
    }

    @Test
    void rejectsNullPersistedCanonicalKey() {
        assertThrows(IllegalArgumentException.class, () -> Address.reconstitute(
                UUID.randomUUID(), "Shelf", null, TYPE_ID, null, true, 0));
    }

    @Test
    void updatesNameAndCanonicalKeyWithoutChangingOtherState() {
        UUID id = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Address address = Address.reconstitute(id, "Drawer", "drawer", TYPE_ID, parentId, false, 5);

        address.updateDetails("  Gaveta Á  ");

        assertEquals("Gaveta Á", address.name());
        assertEquals("gaveta á", address.normalizedNameKey());
        assertEquals(id, address.id());
        assertEquals(TYPE_ID, address.addressTypeId());
        assertEquals(parentId, address.parentId());
        assertFalse(address.active());
        assertEquals(5, address.version());
    }

    @Test
    void rejectedUpdateLeavesExistingDetailsAndKeyUnchanged() {
        Address address = Address.create("Drawer", TYPE_ID, null);

        assertThrows(IllegalArgumentException.class, () -> address.updateDetails("  "));

        assertEquals("Drawer", address.name());
        assertEquals("drawer", address.normalizedNameKey());
    }

    @Test
    void appliesOnlyAnAlreadyAuthorizedLocalParentChange() {
        UUID oldParentId = UUID.randomUUID();
        UUID newParentId = UUID.randomUUID();
        Address address = Address.create("Drawer", TYPE_ID, oldParentId);

        address.applyAuthorizedParentChange(newParentId);

        assertEquals(newParentId, address.parentId());
    }

    @Test
    void applyingCurrentParentIsAnObservableLocalNoOp() {
        UUID id = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Address address = Address.reconstitute(
                id, "Drawer", "drawer", TYPE_ID, parentId, false, 7);

        address.applyAuthorizedParentChange(parentId);

        assertEquals(id, address.id());
        assertEquals("Drawer", address.name());
        assertEquals("drawer", address.normalizedNameKey());
        assertEquals(TYPE_ID, address.addressTypeId());
        assertEquals(parentId, address.parentId());
        assertFalse(address.active());
        assertEquals(7, address.version());
    }

    @Test
    void applyingNullParentToRootIsAnObservableLocalNoOp() {
        UUID id = UUID.randomUUID();
        Address address = Address.reconstitute(
                id, "Root", "root", TYPE_ID, null, true, 4);

        address.applyAuthorizedParentChange(null);

        assertEquals(id, address.id());
        assertEquals("Root", address.name());
        assertEquals("root", address.normalizedNameKey());
        assertEquals(TYPE_ID, address.addressTypeId());
        assertNull(address.parentId());
        assertTrue(address.active());
        assertEquals(4, address.version());
    }

    @Test
    void rejectsSelfParentOnCreationReconstitutionAndAuthorizedParentChange() {
        Address address = Address.create("Drawer", TYPE_ID, null);

        assertThrows(IllegalArgumentException.class,
                () -> address.applyAuthorizedParentChange(address.id()));
        assertThrows(IllegalArgumentException.class, () -> Address.reconstitute(
                address.id(), "Drawer", "drawer", TYPE_ID, address.id(), true, 0));
    }

    @Test
    void activatesAndDeactivatesWithoutChangingOtherAggregateState() {
        UUID id = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Address address = Address.reconstitute(id, "Drawer", "drawer", TYPE_ID, parentId, true, 8);

        address.deactivate();
        assertFalse(address.active());
        assertEquals(id, address.id());
        assertEquals("Drawer", address.name());
        assertEquals("drawer", address.normalizedNameKey());
        assertEquals(TYPE_ID, address.addressTypeId());
        assertEquals(parentId, address.parentId());
        assertEquals(8, address.version());

        address.activate();
        assertTrue(address.active());
        assertEquals(id, address.id());
        assertEquals(TYPE_ID, address.addressTypeId());
        assertEquals(parentId, address.parentId());
        assertEquals(8, address.version());
    }

    @Test
    void lifecycleTransitionsAreIdempotent() {
        Address active = Address.create("Active", TYPE_ID, null);
        Address inactive = Address.reconstitute(
                UUID.randomUUID(), "Inactive", "inactive", TYPE_ID, null, false, 3);

        active.activate();
        inactive.deactivate();

        assertTrue(active.active());
        assertFalse(inactive.active());
        assertEquals(0, active.version());
        assertEquals(3, inactive.version());
    }

    @Test
    void rootAndParentChangesDoNotImplyGraphValidation() {
        UUID unrelatedParentId = UUID.randomUUID();
        Address root = Address.create("Root", TYPE_ID, null);

        root.applyAuthorizedParentChange(unrelatedParentId);
        root.applyAuthorizedParentChange(null);

        assertNull(root.parentId());
    }
}
