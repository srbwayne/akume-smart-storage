package dev.akume.storage.catalog.domain.model;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemTest {

    @Test
    void createsValidActiveItemWithGeneratedIdentityAndInitialVersion() {
        UUID categoryId = UUID.randomUUID();

        Item item = Item.create(" ESP32-S3 development board ", null, categoryId);

        assertNotNull(item.id());
        assertEquals(4, item.id().version());
        assertEquals("ESP32-S3 development board", item.name());
        assertNull(item.description());
        assertEquals(categoryId, item.itemCategoryId());
        assertTrue(item.active());
        assertEquals(0, item.version());
    }

    @Test
    void rejectsNullAndBlankNamesOnCreation() {
        UUID categoryId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> Item.create(null, null, categoryId));
        assertThrows(IllegalArgumentException.class, () -> Item.create(" \t\n ", null, categoryId));
    }

    @Test
    void stripsNameAndPreservesInternalWhitespaceCaseAndPunctuation() {
        Item item = Item.create("  ESP32  S3-Board!  ", null, UUID.randomUUID());

        assertEquals("ESP32  S3-Board!", item.name());
    }

    @Test
    void acceptsOptionalDescriptionAndNormalizesBlankToNull() {
        UUID categoryId = UUID.randomUUID();

        assertNull(Item.create("Cable", null, categoryId).description());
        assertNull(Item.create("Cable", " \t ", categoryId).description());
    }

    @Test
    void stripsDescriptionAndPreservesInternalText() {
        Item item = Item.create("Cable", "  USB-C   braided cable.  ", UUID.randomUUID());

        assertEquals("USB-C   braided cable.", item.description());
    }

    @Test
    void rejectsNullCategoryIdentityOnCreation() {
        assertThrows(IllegalArgumentException.class, () -> Item.create("Cable", null, null));
    }

    @Test
    void reconstitutesSuppliedIdentityInactiveStateAndPersistenceVersion() {
        UUID id = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        Item item = Item.reconstitute(id, "Cable", "USB-C cable", categoryId, false, 23);

        assertEquals(id, item.id());
        assertEquals("Cable", item.name());
        assertEquals("USB-C cable", item.description());
        assertEquals(categoryId, item.itemCategoryId());
        assertFalse(item.active());
        assertEquals(23, item.version());
    }

    @Test
    void rejectsInvalidReconstitutedIdentityNameCategoryAndVersion() {
        UUID id = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> Item.reconstitute(null, "Cable", null, categoryId, true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Item.reconstitute(id, null, null, categoryId, true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Item.reconstitute(id, " \t ", null, categoryId, true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Item.reconstitute(id, "Cable", null, null, true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Item.reconstitute(id, "Cable", null, categoryId, true, -1));
    }

    @Test
    void updateDetailsNormalizesMetadataAndPreservesIdentityCategoryLifecycleAndVersion() {
        UUID id = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Item item = Item.reconstitute(id, "Cable", "Old detail", categoryId, false, 8);

        item.updateDetails("  Braided cable  ", "  Long   internal text  ");

        assertEquals(id, item.id());
        assertEquals("Braided cable", item.name());
        assertEquals("Long   internal text", item.description());
        assertEquals(categoryId, item.itemCategoryId());
        assertFalse(item.active());
        assertEquals(8, item.version());
    }

    @Test
    void updateDetailsRejectsInvalidNameWithoutPartiallyChangingMetadata() {
        UUID categoryId = UUID.randomUUID();
        Item item = Item.create("Cable", "Original", categoryId);

        assertThrows(IllegalArgumentException.class, () -> item.updateDetails(" ", "Changed"));

        assertEquals("Cable", item.name());
        assertEquals("Original", item.description());
    }

    @Test
    void updateDetailsCanClearDescription() {
        Item item = Item.create("Cable", "Original", UUID.randomUUID());

        item.updateDetails("Cable", "  ");

        assertNull(item.description());
    }

    @Test
    void reassignCategoryChangesOnlyCategoryReference() {
        UUID id = UUID.randomUUID();
        UUID originalCategoryId = UUID.randomUUID();
        UUID inactiveCategoryId = UUID.randomUUID();
        Item item = Item.reconstitute(id, "Cable", "Description", originalCategoryId, false, 13);

        item.reassignCategory(inactiveCategoryId);

        assertEquals(id, item.id());
        assertEquals("Cable", item.name());
        assertEquals("Description", item.description());
        assertEquals(inactiveCategoryId, item.itemCategoryId());
        assertFalse(item.active());
        assertEquals(13, item.version());
    }

    @Test
    void rejectsNullCategoryReassignmentWithoutChangingState() {
        UUID categoryId = UUID.randomUUID();
        Item item = Item.create("Cable", "Description", categoryId);

        assertThrows(IllegalArgumentException.class, () -> item.reassignCategory(null));

        assertEquals(categoryId, item.itemCategoryId());
    }

    @Test
    void activationAndDeactivationChangeOnlyLifecycleState() {
        UUID id = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Item item = Item.reconstitute(id, "Cable", null, categoryId, false, 7);

        item.activate();

        assertTrue(item.active());
        assertEquals(id, item.id());
        assertEquals("Cable", item.name());
        assertEquals(categoryId, item.itemCategoryId());
        assertEquals(7, item.version());

        item.deactivate();

        assertFalse(item.active());
        assertEquals(id, item.id());
        assertEquals("Cable", item.name());
        assertEquals(categoryId, item.itemCategoryId());
        assertEquals(7, item.version());
    }

    @Test
    void repeatedLifecycleActionsAreIdempotentAndDoNotIncrementVersion() {
        UUID categoryId = UUID.randomUUID();
        Item active = Item.reconstitute(UUID.randomUUID(), "Active", null, categoryId, true, 5);
        Item inactive = Item.reconstitute(UUID.randomUUID(), "Inactive", null, categoryId, false, 9);

        active.activate();
        inactive.deactivate();

        assertTrue(active.active());
        assertFalse(inactive.active());
        assertEquals(5, active.version());
        assertEquals(9, inactive.version());
    }

    @Test
    void domainDoesNotRejectExistingReferenceToInactiveCategory() {
        UUID inactiveCategoryId = UUID.randomUUID();

        Item item = Item.reconstitute(
                UUID.randomUUID(), "Cable", null, inactiveCategoryId, true, 0);

        assertEquals(inactiveCategoryId, item.itemCategoryId());
        assertTrue(item.active());
    }

    @Test
    void catalogAggregateContainsNoInventoryOrOperationState() {
        Set<String> fields = Arrays.stream(Item.class.getDeclaredFields())
                .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("id", "name", "description", "itemCategoryId", "active", "version"), fields);
    }

    @Test
    void itemNamesAreNotRequiredToBeGloballyUniqueByTheDomain() {
        UUID categoryId = UUID.randomUUID();

        Item first = Item.create("Cable", null, categoryId);
        Item second = Item.create("Cable", null, categoryId);

        assertNotEquals(first.id(), second.id());
        assertEquals(first.name(), second.name());
    }
}
