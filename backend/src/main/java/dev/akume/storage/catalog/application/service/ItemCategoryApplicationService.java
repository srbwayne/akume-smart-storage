package dev.akume.storage.catalog.application.service;

import dev.akume.storage.catalog.application.exception.ItemCategoryConcurrentModificationException;
import dev.akume.storage.catalog.application.exception.ItemCategoryNotFoundException;
import dev.akume.storage.catalog.application.port.in.ActivateItemCategoryCommand;
import dev.akume.storage.catalog.application.port.in.ActivateItemCategoryUseCase;
import dev.akume.storage.catalog.application.port.in.CreateItemCategoryCommand;
import dev.akume.storage.catalog.application.port.in.CreateItemCategoryUseCase;
import dev.akume.storage.catalog.application.port.in.DeactivateItemCategoryCommand;
import dev.akume.storage.catalog.application.port.in.DeactivateItemCategoryUseCase;
import dev.akume.storage.catalog.application.port.in.GetItemCategoryUseCase;
import dev.akume.storage.catalog.application.port.in.ListItemCategoriesUseCase;
import dev.akume.storage.catalog.application.port.in.RenameItemCategoryCommand;
import dev.akume.storage.catalog.application.port.in.RenameItemCategoryUseCase;
import dev.akume.storage.catalog.application.port.out.ItemCategoryRepository;
import dev.akume.storage.catalog.domain.model.ItemCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Orchestrates ItemCategory use cases while leaving invariants to domain and persistence authorities. */
@Service
public class ItemCategoryApplicationService implements
        CreateItemCategoryUseCase,
        ListItemCategoriesUseCase,
        GetItemCategoryUseCase,
        RenameItemCategoryUseCase,
        ActivateItemCategoryUseCase,
        DeactivateItemCategoryUseCase {

    private final ItemCategoryRepository categories;

    public ItemCategoryApplicationService(ItemCategoryRepository categories) {
        this.categories = categories;
    }

    @Override
    @Transactional
    public ItemCategory create(CreateItemCategoryCommand command) {
        ItemCategory category = ItemCategory.create(command.name());
        return categories.insert(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemCategory> listAll() {
        return categories.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public ItemCategory getById(UUID id) {
        return findById(id);
    }

    @Override
    @Transactional
    public ItemCategory rename(RenameItemCategoryCommand command) {
        requireExpectedVersion(command.expectedVersion());
        ItemCategory category = findById(command.id());
        requireCurrentVersion(category, command.expectedVersion());
        category.rename(command.name());
        return categories.update(category).orElseThrow(
                () -> new ItemCategoryNotFoundException(command.id()));
    }

    @Override
    @Transactional
    public ItemCategory activate(ActivateItemCategoryCommand command) {
        requireExpectedVersion(command.expectedVersion());
        ItemCategory category = findById(command.id());
        requireCurrentVersion(category, command.expectedVersion());
        if (category.active()) {
            return category;
        }

        category.activate();
        return categories.update(category).orElseThrow(
                () -> new ItemCategoryNotFoundException(command.id()));
    }

    @Override
    @Transactional
    public ItemCategory deactivate(DeactivateItemCategoryCommand command) {
        requireExpectedVersion(command.expectedVersion());
        ItemCategory category = findById(command.id());
        requireCurrentVersion(category, command.expectedVersion());
        if (!category.active()) {
            return category;
        }

        category.deactivate();
        return categories.update(category).orElseThrow(
                () -> new ItemCategoryNotFoundException(command.id()));
    }

    private ItemCategory findById(UUID id) {
        return categories.findById(id)
                .orElseThrow(() -> new ItemCategoryNotFoundException(id));
    }

    private static void requireExpectedVersion(int expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }

    private static void requireCurrentVersion(ItemCategory category, int expectedVersion) {
        if (category.version() != expectedVersion) {
            throw new ItemCategoryConcurrentModificationException(category.id());
        }
    }
}
