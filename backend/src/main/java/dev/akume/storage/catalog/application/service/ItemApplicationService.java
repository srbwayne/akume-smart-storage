package dev.akume.storage.catalog.application.service;

import dev.akume.storage.catalog.application.exception.InactiveItemCategoryException;
import dev.akume.storage.catalog.application.exception.ItemCategoryNotFoundException;
import dev.akume.storage.catalog.application.exception.ItemConcurrentModificationException;
import dev.akume.storage.catalog.application.exception.ItemNotFoundException;
import dev.akume.storage.catalog.application.port.in.ActivateItemCommand;
import dev.akume.storage.catalog.application.port.in.ActivateItemUseCase;
import dev.akume.storage.catalog.application.port.in.CreateItemCommand;
import dev.akume.storage.catalog.application.port.in.CreateItemUseCase;
import dev.akume.storage.catalog.application.port.in.DeactivateItemCommand;
import dev.akume.storage.catalog.application.port.in.DeactivateItemUseCase;
import dev.akume.storage.catalog.application.port.in.GetItemUseCase;
import dev.akume.storage.catalog.application.port.in.ListItemsUseCase;
import dev.akume.storage.catalog.application.port.in.ReassignItemCategoryCommand;
import dev.akume.storage.catalog.application.port.in.ReassignItemCategoryUseCase;
import dev.akume.storage.catalog.application.port.in.UpdateItemMetadataCommand;
import dev.akume.storage.catalog.application.port.in.UpdateItemMetadataUseCase;
import dev.akume.storage.catalog.application.port.out.ItemCategoryAssignmentLock;
import dev.akume.storage.catalog.application.port.out.ItemRepository;
import dev.akume.storage.catalog.domain.model.Item;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Coordinates Item use cases and category eligibility within persistence transactions. */
@Service
public class ItemApplicationService implements
        CreateItemUseCase,
        ListItemsUseCase,
        GetItemUseCase,
        UpdateItemMetadataUseCase,
        ReassignItemCategoryUseCase,
        ActivateItemUseCase,
        DeactivateItemUseCase {

    private final ItemRepository items;
    private final ItemCategoryAssignmentLock categoryAssignmentLock;

    public ItemApplicationService(
            ItemRepository items,
            ItemCategoryAssignmentLock categoryAssignmentLock) {
        this.items = items;
        this.categoryAssignmentLock = categoryAssignmentLock;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Item create(CreateItemCommand command) {
        Item item = Item.create(command.name(), command.description(), command.itemCategoryId());
        requireActiveCategory(item.itemCategoryId());
        return items.insert(item);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Item> listAll() {
        return items.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Item getById(UUID id) {
        return findById(id);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Item updateMetadata(UpdateItemMetadataCommand command) {
        requireExpectedVersion(command.expectedVersion());
        Item item = findById(command.id());
        requireCurrentVersion(item, command.expectedVersion());
        item.updateDetails(command.name(), command.description());
        return updateOrThrow(item);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Item reassignCategory(ReassignItemCategoryCommand command) {
        requireExpectedVersion(command.expectedVersion());
        Item item = findById(command.id());
        requireCurrentVersion(item, command.expectedVersion());
        item.reassignCategory(command.itemCategoryId());
        requireActiveCategory(item.itemCategoryId());
        return updateOrThrow(item);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Item activate(ActivateItemCommand command) {
        requireExpectedVersion(command.expectedVersion());
        Item item = findById(command.id());
        requireCurrentVersion(item, command.expectedVersion());
        if (item.active()) {
            return item;
        }

        item.activate();
        return updateOrThrow(item);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Item deactivate(DeactivateItemCommand command) {
        requireExpectedVersion(command.expectedVersion());
        Item item = findById(command.id());
        requireCurrentVersion(item, command.expectedVersion());
        if (!item.active()) {
            return item;
        }

        item.deactivate();
        return updateOrThrow(item);
    }

    private void requireActiveCategory(UUID itemCategoryId) {
        boolean active = categoryAssignmentLock.lockAndReadActive(itemCategoryId)
                .orElseThrow(() -> new ItemCategoryNotFoundException(itemCategoryId));
        if (!active) {
            throw new InactiveItemCategoryException(itemCategoryId);
        }
    }

    private Item findById(UUID id) {
        return items.findById(id).orElseThrow(() -> new ItemNotFoundException(id));
    }

    private Item updateOrThrow(Item item) {
        return items.update(item).orElseThrow(() -> new ItemNotFoundException(item.id()));
    }

    private static void requireExpectedVersion(int expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }

    private static void requireCurrentVersion(Item item, int expectedVersion) {
        if (item.version() != expectedVersion) {
            throw new ItemConcurrentModificationException(item.id());
        }
    }
}
