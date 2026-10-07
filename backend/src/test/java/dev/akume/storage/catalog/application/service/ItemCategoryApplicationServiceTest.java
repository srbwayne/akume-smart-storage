package dev.akume.storage.catalog.application.service;

import dev.akume.storage.catalog.application.exception.ItemCategoryConcurrentModificationException;
import dev.akume.storage.catalog.application.exception.ItemCategoryNameAlreadyExistsException;
import dev.akume.storage.catalog.application.exception.ItemCategoryNotFoundException;
import dev.akume.storage.catalog.application.port.in.ActivateItemCategoryCommand;
import dev.akume.storage.catalog.application.port.in.CreateItemCategoryCommand;
import dev.akume.storage.catalog.application.port.in.DeactivateItemCategoryCommand;
import dev.akume.storage.catalog.application.port.in.RenameItemCategoryCommand;
import dev.akume.storage.catalog.application.port.out.ItemCategoryRepository;
import dev.akume.storage.catalog.domain.model.ItemCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemCategoryApplicationServiceTest {

    @Mock
    private ItemCategoryRepository categories;

    private ItemCategoryApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ItemCategoryApplicationService(categories);
    }

    @Test
    void createsActiveCategoryAtVersionZeroAndUsesDomainNameNormalization() {
        when(categories.insert(any(ItemCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemCategory result = service.create(new CreateItemCategoryCommand("  Café  "));

        ArgumentCaptor<ItemCategory> inserted = ArgumentCaptor.forClass(ItemCategory.class);
        verify(categories).insert(inserted.capture());
        assertEquals("Café", inserted.getValue().name());
        assertEquals("café", inserted.getValue().canonicalNameKey());
        assertEquals(result.id(), inserted.getValue().id());
        assertTrue(result.active());
        assertEquals(0, result.version());
    }

    @Test
    void propagatesDuplicateCanonicalNameOnCreate() {
        ItemCategoryNameAlreadyExistsException duplicate = new ItemCategoryNameAlreadyExistsException("Cable");
        when(categories.insert(any(ItemCategory.class))).thenThrow(duplicate);

        assertSame(duplicate, assertThrows(ItemCategoryNameAlreadyExistsException.class,
                () -> service.create(new CreateItemCategoryCommand("Cable"))));
        verify(categories).insert(any(ItemCategory.class));
    }

    @Test
    void listsActiveAndInactiveCategoriesWithoutFiltering() {
        ItemCategory active = category(true, 2);
        ItemCategory inactive = category(false, 7);
        when(categories.findAll()).thenReturn(List.of(active, inactive));

        List<ItemCategory> result = service.listAll();

        verify(categories).findAll();
        assertEquals(List.of(active, inactive), result);
        assertTrue(result.get(0).active());
        assertFalse(result.get(1).active());
    }

    @Test
    void returnsEmptyListWhenNoCategoriesExist() {
        when(categories.findAll()).thenReturn(List.of());

        assertTrue(service.listAll().isEmpty());
        verify(categories).findAll();
    }

    @Test
    void getsExistingCategoryById() {
        ItemCategory existing = category(false, 8);
        when(categories.findById(existing.id())).thenReturn(Optional.of(existing));

        assertSame(existing, service.getById(existing.id()));
        verify(categories).findById(existing.id());
    }

    @Test
    void missingGetThrowsApplicationNotFound() {
        UUID id = UUID.randomUUID();
        when(categories.findById(id)).thenReturn(Optional.empty());

        ItemCategoryNotFoundException error = assertThrows(ItemCategoryNotFoundException.class,
                () -> service.getById(id));

        assertEquals(id, error.itemCategoryId());
    }

    @Test
    void renamesAndReturnsRepositoryManagedVersion() {
        ItemCategory existing = category(true, 6);
        when(categories.findById(existing.id())).thenReturn(Optional.of(existing));
        when(categories.update(any(ItemCategory.class))).thenAnswer(invocation -> {
            ItemCategory changed = invocation.getArgument(0);
            return Optional.of(ItemCategory.reconstitute(changed.id(), changed.name(), changed.active(), 19));
        });

        ItemCategory result = service.rename(new RenameItemCategoryCommand(existing.id(), "Power", 6));

        ArgumentCaptor<ItemCategory> updated = ArgumentCaptor.forClass(ItemCategory.class);
        verify(categories).findById(existing.id());
        verify(categories).update(updated.capture());
        assertEquals("Power", updated.getValue().name());
        assertEquals(6, updated.getValue().version());
        assertEquals(existing.id(), result.id());
        assertEquals("Power", result.name());
        assertEquals(19, result.version());
    }

    @Test
    void staleRenameIsRejectedBeforeRepositoryUpdate() {
        ItemCategory existing = category(true, 4);
        when(categories.findById(existing.id())).thenReturn(Optional.of(existing));

        assertThrows(ItemCategoryConcurrentModificationException.class,
                () -> service.rename(new RenameItemCategoryCommand(existing.id(), "Power", 3)));

        verify(categories).findById(existing.id());
        verify(categories, never()).update(any(ItemCategory.class));
    }

    @Test
    void renamePropagatesDuplicateCanonicalName() {
        ItemCategory existing = category(true, 4);
        ItemCategoryNameAlreadyExistsException duplicate = new ItemCategoryNameAlreadyExistsException("Power");
        when(categories.findById(existing.id())).thenReturn(Optional.of(existing));
        when(categories.update(any(ItemCategory.class))).thenThrow(duplicate);

        assertSame(duplicate, assertThrows(ItemCategoryNameAlreadyExistsException.class,
                () -> service.rename(new RenameItemCategoryCommand(existing.id(), "Power", 4))));
        verify(categories).update(any(ItemCategory.class));
    }

    @Test
    void missingRenameTargetThrowsNotFoundWithoutUpdate() {
        UUID id = UUID.randomUUID();
        when(categories.findById(id)).thenReturn(Optional.empty());

        ItemCategoryNotFoundException error = assertThrows(ItemCategoryNotFoundException.class,
                () -> service.rename(new RenameItemCategoryCommand(id, "Power", 0)));

        assertEquals(id, error.itemCategoryId());
        verify(categories, never()).update(any(ItemCategory.class));
    }

    @Test
    void renameReportsNotFoundIfCategoryDisappearsBeforeUpdate() {
        ItemCategory existing = category(true, 4);
        when(categories.findById(existing.id())).thenReturn(Optional.of(existing));
        when(categories.update(any(ItemCategory.class))).thenReturn(Optional.empty());

        assertThrows(ItemCategoryNotFoundException.class,
                () -> service.rename(new RenameItemCategoryCommand(existing.id(), "Power", 4)));
        verify(categories).update(any(ItemCategory.class));
    }

    @Test
    void activatesInactiveCategoryAndReturnsRepositoryManagedVersion() {
        ItemCategory existing = category(false, 5);
        when(categories.findById(existing.id())).thenReturn(Optional.of(existing));
        when(categories.update(any(ItemCategory.class))).thenAnswer(invocation -> {
            ItemCategory changed = invocation.getArgument(0);
            return Optional.of(ItemCategory.reconstitute(changed.id(), changed.name(), changed.active(), 13));
        });

        ItemCategory result = service.activate(new ActivateItemCategoryCommand(existing.id(), 5));

        ArgumentCaptor<ItemCategory> updated = ArgumentCaptor.forClass(ItemCategory.class);
        verify(categories).update(updated.capture());
        assertTrue(updated.getValue().active());
        assertEquals(5, updated.getValue().version());
        assertTrue(result.active());
        assertEquals(13, result.version());
    }

    @Test
    void activatingAlreadyActiveCategoryIsNoOpWithoutUpdateOrVersionChange() {
        ItemCategory existing = category(true, 9);
        when(categories.findById(existing.id())).thenReturn(Optional.of(existing));

        ItemCategory result = service.activate(new ActivateItemCategoryCommand(existing.id(), 9));

        verify(categories, never()).update(any(ItemCategory.class));
        assertSame(existing, result);
        assertTrue(result.active());
        assertEquals(9, result.version());
    }

    @Test
    void staleActivationIsRejectedEvenWhenCategoryIsAlreadyActive() {
        ItemCategory existing = category(true, 9);
        when(categories.findById(existing.id())).thenReturn(Optional.of(existing));

        assertThrows(ItemCategoryConcurrentModificationException.class,
                () -> service.activate(new ActivateItemCategoryCommand(existing.id(), 8)));

        verify(categories, never()).update(any(ItemCategory.class));
    }

    @Test
    void missingActivationTargetThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(categories.findById(id)).thenReturn(Optional.empty());

        assertThrows(ItemCategoryNotFoundException.class,
                () -> service.activate(new ActivateItemCategoryCommand(id, 0)));
        verify(categories, never()).update(any(ItemCategory.class));
    }

    @Test
    void activationReportsNotFoundIfCategoryDisappearsBeforeUpdate() {
        ItemCategory existing = category(false, 3);
        when(categories.findById(existing.id())).thenReturn(Optional.of(existing));
        when(categories.update(any(ItemCategory.class))).thenReturn(Optional.empty());

        assertThrows(ItemCategoryNotFoundException.class,
                () -> service.activate(new ActivateItemCategoryCommand(existing.id(), 3)));
        verify(categories).update(any(ItemCategory.class));
    }

    @Test
    void propagatesPersistenceConcurrencyFailureDuringActivationWithoutRetry() {
        ItemCategory existing = category(false, 11);
        ItemCategoryConcurrentModificationException conflict =
                new ItemCategoryConcurrentModificationException(existing.id());
        when(categories.findById(existing.id())).thenReturn(Optional.of(existing));
        when(categories.update(any(ItemCategory.class))).thenThrow(conflict);

        assertSame(conflict, assertThrows(ItemCategoryConcurrentModificationException.class,
                () -> service.activate(new ActivateItemCategoryCommand(existing.id(), existing.version()))));

        verify(categories, times(1)).findById(existing.id());
        verify(categories, times(1)).update(any(ItemCategory.class));
        verify(categories, never()).insert(any(ItemCategory.class));
    }

    @Test
    void deactivatesActiveCategoryWithoutCheckingItemUsageAndReturnsPersistedVersion() {
        ItemCategory existing = category(true, 12);
        when(categories.findById(existing.id())).thenReturn(Optional.of(existing));
        when(categories.update(any(ItemCategory.class))).thenAnswer(invocation -> {
            ItemCategory changed = invocation.getArgument(0);
            return Optional.of(ItemCategory.reconstitute(changed.id(), changed.name(), changed.active(), 21));
        });

        ItemCategory result = service.deactivate(new DeactivateItemCategoryCommand(existing.id(), 12));

        ArgumentCaptor<ItemCategory> updated = ArgumentCaptor.forClass(ItemCategory.class);
        verify(categories).update(updated.capture());
        assertFalse(updated.getValue().active());
        assertEquals(12, updated.getValue().version());
        assertFalse(result.active());
        assertEquals(21, result.version());
    }

    @Test
    void deactivatingAlreadyInactiveCategoryIsNoOpWithoutUpdateOrVersionChange() {
        ItemCategory existing = category(false, 15);
        when(categories.findById(existing.id())).thenReturn(Optional.of(existing));

        ItemCategory result = service.deactivate(new DeactivateItemCategoryCommand(existing.id(), 15));

        verify(categories, never()).update(any(ItemCategory.class));
        assertSame(existing, result);
        assertFalse(result.active());
        assertEquals(15, result.version());
    }

    @Test
    void staleDeactivationIsRejectedEvenWhenCategoryIsAlreadyInactive() {
        ItemCategory existing = category(false, 15);
        when(categories.findById(existing.id())).thenReturn(Optional.of(existing));

        assertThrows(ItemCategoryConcurrentModificationException.class,
                () -> service.deactivate(new DeactivateItemCategoryCommand(existing.id(), 14)));

        verify(categories, never()).update(any(ItemCategory.class));
    }

    @Test
    void missingDeactivationTargetThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(categories.findById(id)).thenReturn(Optional.empty());

        assertThrows(ItemCategoryNotFoundException.class,
                () -> service.deactivate(new DeactivateItemCategoryCommand(id, 0)));
        verify(categories, never()).update(any(ItemCategory.class));
    }

    @Test
    void deactivationReportsNotFoundIfCategoryDisappearsBeforeUpdate() {
        ItemCategory existing = category(true, 3);
        when(categories.findById(existing.id())).thenReturn(Optional.of(existing));
        when(categories.update(any(ItemCategory.class))).thenReturn(Optional.empty());

        assertThrows(ItemCategoryNotFoundException.class,
                () -> service.deactivate(new DeactivateItemCategoryCommand(existing.id(), 3)));
        verify(categories).update(any(ItemCategory.class));
    }

    @Test
    void propagatesPersistenceConcurrencyFailureDuringDeactivationWithoutRetry() {
        ItemCategory existing = category(true, 17);
        ItemCategoryConcurrentModificationException conflict =
                new ItemCategoryConcurrentModificationException(existing.id());
        when(categories.findById(existing.id())).thenReturn(Optional.of(existing));
        when(categories.update(any(ItemCategory.class))).thenThrow(conflict);

        assertSame(conflict, assertThrows(ItemCategoryConcurrentModificationException.class,
                () -> service.deactivate(new DeactivateItemCategoryCommand(existing.id(), existing.version()))));

        verify(categories, times(1)).findById(existing.id());
        verify(categories, times(1)).update(any(ItemCategory.class));
        verify(categories, never()).insert(any(ItemCategory.class));
    }

    @Test
    void negativeRenameVersionIsRejectedBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> service.rename(new RenameItemCategoryCommand(UUID.randomUUID(), "Power", -1)));
        verifyNoInteractions(categories);
    }

    @Test
    void negativeActivationVersionIsRejectedBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> service.activate(new ActivateItemCategoryCommand(UUID.randomUUID(), -1)));
        verifyNoInteractions(categories);
    }

    @Test
    void negativeDeactivationVersionIsRejectedBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> service.deactivate(new DeactivateItemCategoryCommand(UUID.randomUUID(), -1)));
        verifyNoInteractions(categories);
    }

    @Test
    void propagatesPersistenceConcurrencyFailureWithoutRetry() {
        ItemCategory existing = category(true, 5);
        ItemCategoryConcurrentModificationException conflict =
                new ItemCategoryConcurrentModificationException(existing.id());
        when(categories.findById(existing.id())).thenReturn(Optional.of(existing));
        when(categories.update(any(ItemCategory.class))).thenThrow(conflict);

        assertSame(conflict, assertThrows(ItemCategoryConcurrentModificationException.class,
                () -> service.rename(new RenameItemCategoryCommand(existing.id(), "Power", 5))));

        verify(categories, times(1)).findById(existing.id());
        verify(categories, times(1)).update(any(ItemCategory.class));
        verify(categories, never()).insert(any(ItemCategory.class));
    }

    private static ItemCategory category(boolean active, int version) {
        return ItemCategory.reconstitute(UUID.randomUUID(), "Cable", active, version);
    }
}
