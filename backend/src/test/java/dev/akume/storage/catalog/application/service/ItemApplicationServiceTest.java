package dev.akume.storage.catalog.application.service;

import dev.akume.storage.catalog.application.exception.InactiveItemCategoryException;
import dev.akume.storage.catalog.application.exception.ItemCategoryNotFoundException;
import dev.akume.storage.catalog.application.exception.ItemConcurrentModificationException;
import dev.akume.storage.catalog.application.exception.ItemNotFoundException;
import dev.akume.storage.catalog.application.model.ItemReadView;
import dev.akume.storage.catalog.application.port.in.ActivateItemCommand;
import dev.akume.storage.catalog.application.port.in.CreateItemCommand;
import dev.akume.storage.catalog.application.port.in.DeactivateItemCommand;
import dev.akume.storage.catalog.application.port.in.ReassignItemCategoryCommand;
import dev.akume.storage.catalog.application.port.in.UpdateItemMetadataCommand;
import dev.akume.storage.catalog.application.port.out.ItemCategoryAssignmentLock;
import dev.akume.storage.catalog.application.port.out.ItemRepository;
import dev.akume.storage.catalog.application.port.out.ItemReadProjectionRepository;
import dev.akume.storage.catalog.domain.model.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemApplicationServiceTest {

    @Mock
    private ItemRepository items;

    @Mock
    private ItemCategoryAssignmentLock categoryLock;

    @Mock
    private ItemReadProjectionRepository itemViews;

    private ItemApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ItemApplicationService(items, categoryLock, itemViews);
    }

    @Test
    void createsItemOnlyAfterLockedCategoryIsConfirmedActive() {
        UUID categoryId = UUID.randomUUID();
        when(categoryLock.lockAndReadActive(categoryId)).thenReturn(Optional.of(true));
        java.util.concurrent.atomic.AtomicReference<Item> insertedItem = new java.util.concurrent.atomic.AtomicReference<>();
        when(items.insert(any(Item.class))).thenAnswer(invocation -> {
            Item inserted = invocation.getArgument(0);
            insertedItem.set(inserted);
            return inserted;
        });
        when(itemViews.findById(any())).thenAnswer(invocation -> Optional.of(view(insertedItem.get())));

        ItemReadView result = service.create(new CreateItemCommand("  Board  ", "  Dev kit ", categoryId));

        InOrder order = inOrder(categoryLock, items);
        order.verify(categoryLock).lockAndReadActive(categoryId);
        ArgumentCaptor<Item> inserted = ArgumentCaptor.forClass(Item.class);
        order.verify(items).insert(inserted.capture());
        assertEquals("Board", inserted.getValue().name());
        assertEquals("Dev kit", inserted.getValue().description());
        assertEquals(categoryId, inserted.getValue().itemCategoryId());
        assertTrue(inserted.getValue().active());
        assertEquals(0, inserted.getValue().version());
        assertEquals(inserted.getValue().id(), result.id());
        assertEquals(categoryId, result.category().id());
    }

    @Test
    void rejectsCreationWhenLockedCategoryIsInactive() {
        UUID categoryId = UUID.randomUUID();
        when(categoryLock.lockAndReadActive(categoryId)).thenReturn(Optional.of(false));

        assertThrows(InactiveItemCategoryException.class,
                () -> service.create(new CreateItemCommand("Board", null, categoryId)));

        verify(items, never()).insert(any(Item.class));
    }

    @Test
    void rejectsCreationWhenCategoryDoesNotExist() {
        UUID categoryId = UUID.randomUUID();
        when(categoryLock.lockAndReadActive(categoryId)).thenReturn(Optional.empty());

        assertThrows(ItemCategoryNotFoundException.class,
                () -> service.create(new CreateItemCommand("Board", null, categoryId)));

        verify(items, never()).insert(any(Item.class));
    }

    @Test
    void rejectsInvalidDomainInputBeforeTakingCategoryLock() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new CreateItemCommand("  ", null, UUID.randomUUID())));
        verifyNoInteractions(categoryLock, items);
    }

    @Test
    void listsActiveAndInactiveItemsAndGetsById() {
        Item active = item(true, 0);
        Item inactive = item(false, 1);
        when(itemViews.findAll()).thenReturn(List.of(view(active), view(inactive)));
        when(itemViews.findById(active.id())).thenReturn(Optional.of(view(active)));

        assertEquals(List.of(view(active), view(inactive)), service.listAll());
        assertEquals(view(active), service.getById(active.id()));
        verifyNoInteractions(categoryLock);
    }

    @Test
    void missingItemReadsUseApplicationNotFoundException() {
        UUID id = UUID.randomUUID();
        when(itemViews.findById(id)).thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class, () -> service.getById(id));
    }

    @Test
    void metadataUpdatePreservesCategoryAndUsesAuthoritativeRepositorySnapshot() {
        Item stored = item(true, 4);
        when(items.findById(stored.id())).thenReturn(Optional.of(stored));
        when(items.update(any(Item.class))).thenAnswer(invocation -> {
            Item updated = invocation.getArgument(0);
            return Optional.of(Item.reconstitute(updated.id(), updated.name(), updated.description(),
                    updated.itemCategoryId(), updated.active(), 5));
        });

        when(itemViews.findById(stored.id())).thenReturn(Optional.of(new ItemReadView(
                stored.id(), "New name", "New detail", stored.itemCategoryId(), true, 5,
                new ItemReadView.CategorySummary(stored.itemCategoryId(), "Category", true))));
        ItemReadView result = service.updateMetadata(
                new UpdateItemMetadataCommand(stored.id(), " New name ", " New detail ", 4));

        ArgumentCaptor<Item> changed = ArgumentCaptor.forClass(Item.class);
        verify(items).update(changed.capture());
        assertEquals(stored.id(), changed.getValue().id());
        assertEquals(stored.itemCategoryId(), changed.getValue().itemCategoryId());
        assertEquals("New name", result.name());
        assertEquals("New detail", result.description());
        assertEquals(5, result.version());
        verifyNoInteractions(categoryLock);
    }

    @Test
    void reassignmentChecksExpectedVersionThenLocksTargetBeforeUpdate() {
        Item stored = item(true, 2);
        UUID targetCategoryId = UUID.randomUUID();
        when(items.findById(stored.id())).thenReturn(Optional.of(stored));
        when(categoryLock.lockAndReadActive(targetCategoryId)).thenReturn(Optional.of(true));
        when(items.update(any(Item.class))).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        when(itemViews.findById(stored.id())).thenReturn(Optional.of(new ItemReadView(
                stored.id(), stored.name(), stored.description(), targetCategoryId, true, 2,
                new ItemReadView.CategorySummary(targetCategoryId, "Target", true))));
        ItemReadView result = service.reassignCategory(
                new ReassignItemCategoryCommand(stored.id(), targetCategoryId, 2));

        InOrder order = inOrder(items, categoryLock);
        order.verify(items).findById(stored.id());
        order.verify(categoryLock).lockAndReadActive(targetCategoryId);
        order.verify(items).update(any(Item.class));
        assertEquals(targetCategoryId, result.itemCategoryId());
        assertEquals(stored.id(), result.id());
        assertEquals(stored.version(), result.version());
    }

    @Test
    void reassignmentRejectsInactiveOrMissingTargetWithoutUpdatingItem() {
        Item stored = item(true, 2);
        UUID targetCategoryId = UUID.randomUUID();
        when(items.findById(stored.id())).thenReturn(Optional.of(stored));
        when(categoryLock.lockAndReadActive(targetCategoryId)).thenReturn(Optional.of(false));

        assertThrows(InactiveItemCategoryException.class, () -> service.reassignCategory(
                new ReassignItemCategoryCommand(stored.id(), targetCategoryId, 2)));
        verify(items, never()).update(any(Item.class));

        when(categoryLock.lockAndReadActive(targetCategoryId)).thenReturn(Optional.empty());
        assertThrows(ItemCategoryNotFoundException.class, () -> service.reassignCategory(
                new ReassignItemCategoryCommand(stored.id(), targetCategoryId, 2)));
        verify(items, never()).update(any(Item.class));
    }

    @Test
    void staleExpectedVersionIsRejectedBeforeTargetCategoryLock() {
        Item stored = item(true, 3);
        when(items.findById(stored.id())).thenReturn(Optional.of(stored));

        assertThrows(ItemConcurrentModificationException.class, () -> service.reassignCategory(
                new ReassignItemCategoryCommand(stored.id(), UUID.randomUUID(), 2)));

        verifyNoInteractions(categoryLock);
        verify(items, never()).update(any(Item.class));
    }

    @Test
    void lifecycleIsIdempotentAndDoesNotCheckExistingCategoryEligibility() {
        Item active = item(true, 5);
        Item inactive = item(false, 6);
        when(items.findById(active.id())).thenReturn(Optional.of(active));
        when(items.findById(inactive.id())).thenReturn(Optional.of(inactive));
        when(itemViews.findById(active.id())).thenReturn(Optional.of(view(active)));
        when(itemViews.findById(inactive.id())).thenReturn(Optional.of(view(inactive)));

        assertEquals(view(active), service.activate(new ActivateItemCommand(active.id(), 5)));
        assertEquals(view(inactive), service.deactivate(new DeactivateItemCommand(inactive.id(), 6)));

        verify(items, never()).update(any(Item.class));
        verifyNoInteractions(categoryLock);
    }

    @Test
    void staleExpectedVersionIsRejectedBeforeLifecycleNoOp() {
        Item active = item(true, 5);
        when(items.findById(active.id())).thenReturn(Optional.of(active));

        assertThrows(ItemConcurrentModificationException.class,
                () -> service.activate(new ActivateItemCommand(active.id(), 4)));
        verify(items, never()).update(any(Item.class));
        verifyNoInteractions(categoryLock);
    }

    @Test
    void negativeExpectedVersionsAreRejectedBeforeRepositoryAccess() {
        UUID id = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> service.updateMetadata(new UpdateItemMetadataCommand(id, "X", null, -1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.reassignCategory(new ReassignItemCategoryCommand(id, UUID.randomUUID(), -1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.activate(new ActivateItemCommand(id, -1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.deactivate(new DeactivateItemCommand(id, -1)));

        verifyNoInteractions(items, categoryLock);
    }

    private static Item item(boolean active, int version) {
        return Item.reconstitute(UUID.randomUUID(), "Board", null, UUID.randomUUID(), active, version);
    }

    private static ItemReadView view(Item item) {
        return new ItemReadView(item.id(), item.name(), item.description(), item.itemCategoryId(), item.active(),
                item.version(), new ItemReadView.CategorySummary(item.itemCategoryId(), "Category", true));
    }
}
