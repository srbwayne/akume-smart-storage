package dev.akume.storage.catalog.domain.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemCategoryTest {

    @Test
    void createsActiveCategoryWithGeneratedIdentityInitialVersionAndStrippedName() {
        ItemCategory category = ItemCategory.create(" Cable ");

        assertNotNull(category.id());
        assertEquals(4, category.id().version());
        assertEquals("Cable", category.name());
        assertEquals("cable", category.canonicalNameKey());
        assertTrue(category.active());
        assertEquals(0, category.version());
    }

    @Test
    void rejectsNullAndBlankNamesOnCreationAndRename() {
        assertThrows(IllegalArgumentException.class, () -> ItemCategory.create(null));
        assertThrows(IllegalArgumentException.class, () -> ItemCategory.create(" \t\n "));

        ItemCategory category = ItemCategory.create("Cable");
        assertThrows(IllegalArgumentException.class, () -> category.rename(null));
        assertThrows(IllegalArgumentException.class, () -> category.rename(" \t\n "));
        assertEquals("Cable", category.name());
    }

    @Test
    void canonicalKeyIgnoresCaseAndOuterWhitespace() {
        ItemCategory spaced = ItemCategory.create(" Cable ");
        ItemCategory lowerCase = ItemCategory.create("cable");

        assertEquals(spaced.canonicalNameKey(), lowerCase.canonicalNameKey());
    }

    @Test
    void canonicalKeyUsesNfcForComposedAndDecomposedNames() {
        String composed = "Café";
        String decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD);
        ItemCategory composedCategory = ItemCategory.create(composed);
        ItemCategory decomposedCategory = ItemCategory.create(decomposed);

        assertEquals(composedCategory.canonicalNameKey(), decomposedCategory.canonicalNameKey());
        assertEquals("Café", composedCategory.name());
        assertEquals("Café", decomposedCategory.name());
        assertEquals("café", composedCategory.canonicalNameKey());
        assertEquals("café", decomposedCategory.canonicalNameKey());
        assertEquals(composedCategory.name(), decomposedCategory.name());
    }

    @Test
    void canonicalKeyPreservesAccentDistinction() {
        ItemCategory accented = ItemCategory.create("Armário");
        ItemCategory unaccented = ItemCategory.create("armario");

        assertNotEquals(accented.canonicalNameKey(), unaccented.canonicalNameKey());
    }

    @Test
    void canonicalKeyPreservesInternalWhitespaceAndPunctuation() {
        ItemCategory doubledSpace = ItemCategory.create("Sala  Principal");
        ItemCategory singleSpace = ItemCategory.create("Sala Principal");
        ItemCategory punctuated = ItemCategory.create("Cable-1");
        ItemCategory unpunctuated = ItemCategory.create("Cable1");

        assertNotEquals(doubledSpace.canonicalNameKey(), singleSpace.canonicalNameKey());
        assertNotEquals(punctuated.canonicalNameKey(), unpunctuated.canonicalNameKey());
        assertEquals("Sala  Principal", doubledSpace.name());
    }

    @Test
    void canonicalKeyDoesNotDependOnDefaultLocale() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            ItemCategory category = ItemCategory.create("ISLAND");

            assertEquals("island", category.canonicalNameKey());
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void reconstitutesSuppliedIdentityDisplayLifecycleAndVersion() {
        UUID id = UUID.randomUUID();
        ItemCategory category = ItemCategory.reconstitute(id, "Armário", false, 23);

        assertEquals(id, category.id());
        assertEquals("Armário", category.name());
        assertEquals("armário", category.canonicalNameKey());
        assertFalse(category.active());
        assertEquals(23, category.version());
    }

    @Test
    void rejectsNullIdentityUnacceptedPersistedNameAndNegativeVersion() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemCategory.reconstitute(null, "Cable", true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ItemCategory.reconstitute(UUID.randomUUID(), null, true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ItemCategory.reconstitute(UUID.randomUUID(), " Cable ", true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ItemCategory.reconstitute(UUID.randomUUID(), "Cable", true, -1));
    }

    @Test
    void reconstitutionAcceptsNfcNameAndRejectsDecomposedPersistedName() {
        String nfcName = "Café";
        String nfdName = Normalizer.normalize(nfcName, Normalizer.Form.NFD);

        ItemCategory category = ItemCategory.reconstitute(UUID.randomUUID(), nfcName, true, 4);

        assertEquals(nfcName, category.name());
        assertEquals("café", category.canonicalNameKey());
        assertThrows(IllegalArgumentException.class,
                () -> ItemCategory.reconstitute(UUID.randomUUID(), nfdName, true, 4));
    }

    @Test
    void renameChangesDisplayAndCanonicalKeyWithoutChangingIdentityLifecycleOrVersion() {
        UUID id = UUID.randomUUID();
        ItemCategory category = ItemCategory.reconstitute(id, "Cable", false, 17);

        category.rename("  Armário  ");

        assertEquals(id, category.id());
        assertEquals("Armário", category.name());
        assertEquals("armário", category.canonicalNameKey());
        assertFalse(category.active());
        assertEquals(17, category.version());
    }

    @Test
    void lifecycleTransitionsPreserveIdentityNameAndVersion() {
        UUID id = UUID.randomUUID();
        ItemCategory category = ItemCategory.reconstitute(id, "Cable", true, 11);

        category.deactivate();

        assertFalse(category.active());
        assertEquals(id, category.id());
        assertEquals("Cable", category.name());
        assertEquals(11, category.version());

        category.activate();

        assertTrue(category.active());
        assertEquals(id, category.id());
        assertEquals("Cable", category.name());
        assertEquals(11, category.version());
    }

    @Test
    void lifecycleNoOpsPreserveVersionAndOtherState() {
        UUID activeId = UUID.randomUUID();
        UUID inactiveId = UUID.randomUUID();
        ItemCategory active = ItemCategory.reconstitute(activeId, "Active", true, 5);
        ItemCategory inactive = ItemCategory.reconstitute(inactiveId, "Inactive", false, 8);

        active.activate();
        inactive.deactivate();

        assertTrue(active.active());
        assertFalse(inactive.active());
        assertEquals(activeId, active.id());
        assertEquals(inactiveId, inactive.id());
        assertEquals("Active", active.name());
        assertEquals("Inactive", inactive.name());
        assertEquals(5, active.version());
        assertEquals(8, inactive.version());
    }
}
