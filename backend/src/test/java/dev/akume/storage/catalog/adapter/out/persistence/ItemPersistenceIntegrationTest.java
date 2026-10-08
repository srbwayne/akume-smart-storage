package dev.akume.storage.catalog.adapter.out.persistence;

import dev.akume.storage.catalog.application.exception.ItemConcurrentModificationException;
import dev.akume.storage.catalog.application.port.out.ItemCategoryAssignmentLock;
import dev.akume.storage.catalog.application.port.out.ItemRepository;
import dev.akume.storage.catalog.application.port.out.ItemCategoryRepository;
import dev.akume.storage.catalog.domain.model.Item;
import dev.akume.storage.catalog.domain.model.ItemCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ItemPersistenceIntegrationTest {

    @Autowired
    private ItemRepository items;

    @Autowired
    private ItemCategoryRepository categories;

    @Autowired
    private SpringDataItemRepository jpaItems;

    @Autowired
    private SpringDataItemCategoryRepository jpaCategories;

    @Autowired
    private ItemCategoryAssignmentLock categoryAssignmentLock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    @AfterEach
    void clearItemsAndCategories() {
        jpaItems.deleteAllInBatch();
        jpaCategories.deleteAllInBatch();
    }

    @Test
    void flywayCreatesExpectedItemSchemaAndRestrictiveConstraints() {
        assertEquals("uuid", columnType("id"));
        assertEquals("text", columnType("name"));
        assertEquals("text", columnType("description"));
        assertEquals("uuid", columnType("item_category_id"));
        assertEquals("boolean", columnType("active"));
        assertEquals("integer", columnType("version"));

        assertEquals(1, constraintCount("pk_items", "p"));
        assertEquals(1, constraintCount("fk_items_item_category_id", "f"));
        assertEquals(1, constraintCount("ck_items_version_non_negative", "c"));
        assertEquals(0, constraintCountForType("u"));

        String deleteRule = jdbcTemplate.queryForObject(
                "SELECT confdeltype::text FROM pg_constraint "
                        + "WHERE conrelid = 'items'::regclass AND conname = ?",
                String.class,
                "fk_items_item_category_id");
        assertEquals("r", deleteRule);

        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'items' ORDER BY indexname",
                String.class);
        assertEquals(List.of("pk_items"), indexes);
    }

    @Test
    void rejectsItemWithUnknownCategoryThroughForeignKey() {
        Item item = Item.create("Cable", null, UUID.randomUUID());

        assertThrows(DataIntegrityViolationException.class, () -> items.insert(item));
        assertTrue(items.findAll().isEmpty());
    }

    @Test
    void databaseRejectsNegativeItemVersion() {
        UUID categoryId = insertCategory("Hardware", true).id();

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO items (id, name, description, item_category_id, active, version) "
                                + "VALUES (?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), "Invalid version", null, categoryId, true, -1));
        assertTrue(items.findAll().isEmpty());
    }

    @Test
    void insertsAndReconstructsItemWithProviderInitializedVersionAndNullableDescription() {
        UUID categoryId = insertCategory("Hardware", true).id();
        Item source = Item.create("  Cable  ", null, categoryId);

        Item inserted = items.insert(source);
        Item reloaded = items.findById(inserted.id()).orElseThrow();

        assertEquals(source.id(), inserted.id());
        assertEquals("Cable", reloaded.name());
        assertNull(reloaded.description());
        assertEquals(categoryId, reloaded.itemCategoryId());
        assertTrue(reloaded.active());
        assertEquals(0, inserted.version());
        assertEquals(0, reloaded.version());
    }

    @Test
    void allowsDuplicateItemNames() {
        UUID categoryId = insertCategory("Hardware", true).id();

        Item first = items.insert(Item.create("Cable", null, categoryId));
        Item second = items.insert(Item.create("Cable", "Another definition", categoryId));

        assertNotEquals(first.id(), second.id());
        assertEquals(2, items.findAll().size());
    }

    @Test
    void retainsReferenceToInactiveCategoryAndListsActiveAndInactiveItems() {
        UUID inactiveCategoryId = insertCategory("Inactive", false).id();
        UUID activeCategoryId = insertCategory("Active", true).id();

        Item inactiveItem = Item.reconstitute(
                UUID.randomUUID(), "Inactive-category Item", null, inactiveCategoryId, false, 0);
        Item activeItem = Item.create("Active-category Item", null, activeCategoryId);
        items.insert(inactiveItem);
        items.insert(activeItem);

        List<Item> listed = items.findAll();

        assertEquals(2, listed.size());
        assertTrue(listed.stream().anyMatch(item -> item.id().equals(inactiveItem.id())
                && item.itemCategoryId().equals(inactiveCategoryId) && !item.active()));
        assertTrue(listed.stream().anyMatch(item -> item.id().equals(activeItem.id()) && item.active()));
        assertFalse(categories.findById(inactiveCategoryId).orElseThrow().active());
    }

    @Test
    void persistsMetadataUpdateAndCategoryReassignment() {
        UUID originalCategoryId = insertCategory("Original", true).id();
        UUID targetCategoryId = insertCategory("Target", true).id();
        Item created = items.insert(Item.create("Cable", null, originalCategoryId));

        Item metadata = Item.reconstitute(
                created.id(), "Braided cable", "USB-C", originalCategoryId, true, created.version());
        Item updated = items.update(metadata).orElseThrow();
        assertEquals(1, updated.version());
        assertEquals("Braided cable", updated.name());
        assertEquals("USB-C", updated.description());

        Item reassigned = Item.reconstitute(
                updated.id(), updated.name(), updated.description(), targetCategoryId,
                updated.active(), updated.version());
        Item persisted = items.update(reassigned).orElseThrow();

        assertEquals(2, persisted.version());
        assertEquals(targetCategoryId, persisted.itemCategoryId());
        assertEquals("Braided cable", persisted.name());
        assertEquals("USB-C", persisted.description());
    }

    @Test
    void persistsActivationAndDeactivationWithProviderManagedVersions() {
        UUID categoryId = insertCategory("Hardware", true).id();
        Item created = items.insert(Item.create("Cable", null, categoryId));

        Item inactive = Item.reconstitute(
                created.id(), created.name(), created.description(), categoryId, false, created.version());
        Item deactivated = items.update(inactive).orElseThrow();
        assertFalse(deactivated.active());
        assertEquals(1, deactivated.version());

        Item active = Item.reconstitute(
                deactivated.id(), deactivated.name(), deactivated.description(), categoryId,
                true, deactivated.version());
        Item activated = items.update(active).orElseThrow();
        assertTrue(activated.active());
        assertEquals(2, activated.version());
    }

    @Test
    void rejectsStaleSnapshotWithoutOverwritingCommittedItem() {
        UUID categoryId = insertCategory("Hardware", true).id();
        Item created = items.insert(Item.create("Cable", null, categoryId));
        Item stale = items.findById(created.id()).orElseThrow();
        Item changed = Item.reconstitute(
                created.id(), "Power cable", null, categoryId, true, created.version());

        Item persisted = items.update(changed).orElseThrow();

        assertEquals(1, persisted.version());
        assertThrows(ItemConcurrentModificationException.class, () -> items.update(stale));
        assertEquals("Power cable", items.findById(created.id()).orElseThrow().name());
        assertEquals(1, items.findById(created.id()).orElseThrow().version());
    }

    @Test
    void updateReturnsEmptyWhenItemDoesNotExist() {
        UUID categoryId = insertCategory("Hardware", true).id();
        Item missing = Item.create("Missing", null, categoryId);

        assertTrue(items.update(missing).isEmpty());
    }

    @Test
    void noOpLifecycleUpdateDoesNotAdvancePersistenceVersion() {
        UUID categoryId = insertCategory("Hardware", true).id();
        Item created = items.insert(Item.create("Cable", null, categoryId));

        Item inactive = Item.reconstitute(
                created.id(), created.name(), created.description(), categoryId, false, created.version());
        Item deactivated = items.update(inactive).orElseThrow();
        Item unchanged = Item.reconstitute(
                deactivated.id(), deactivated.name(), deactivated.description(), categoryId,
                false, deactivated.version());

        Item noOpResult = items.update(unchanged).orElseThrow();

        assertFalse(noOpResult.active());
        assertEquals(1, noOpResult.version());
        assertEquals(1, items.findById(created.id()).orElseThrow().version());
    }

    @Test
    void categoryLockReportsMissingAndCurrentActiveState() {
        UUID missingId = UUID.randomUUID();
        assertTrue(lockInTransaction(missingId).isEmpty());

        ItemCategory active = insertCategory("Active", true);
        ItemCategory inactive = insertCategory("Inactive", false);
        assertEquals(Optional.of(true), lockInTransaction(active.id()));
        assertEquals(Optional.of(false), lockInTransaction(inactive.id()));
    }

    @Test
    void categoryRowLockBlocksDeactivationUntilOwningTransactionCompletes() throws Exception {
        ItemCategory category = insertCategory("Lock target", true);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch deactivationReady = new CountDownLatch(1);
        AtomicInteger blockerPid = new AtomicInteger();
        AtomicInteger waiterPid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        try {
            Future<?> lockOwner = executor.submit(() -> transaction.executeWithoutResult(status -> {
                blockerPid.set(currentBackendPid());
                assertEquals(Optional.of(true), categoryAssignmentLock.lockAndReadActive(category.id()));
                lockAcquired.countDown();
                await(releaseLock, "category lock release");
            }));

            assertTrue(lockAcquired.await(10, TimeUnit.SECONDS), "assignment lock should be acquired");

            Future<?> deactivation = executor.submit(() -> transaction.executeWithoutResult(status -> {
                waiterPid.set(currentBackendPid());
                deactivationReady.countDown();
                ItemCategory inactive = ItemCategory.reconstitute(
                        category.id(), category.name(), false, category.version());
                categories.update(inactive).orElseThrow();
            }));

            assertTrue(deactivationReady.await(10, TimeUnit.SECONDS), "deactivation should start");
            assertTrue(awaitDatabaseBlocking(blockerPid.get(), waiterPid.get()),
                    "category update should wait on the assignment row lock");
            assertFalse(deactivation.isDone(), "deactivation must remain blocked while the lock is held");

            releaseLock.countDown();
            lockOwner.get(10, TimeUnit.SECONDS);
            deactivation.get(10, TimeUnit.SECONDS);

            assertFalse(categories.findById(category.id()).orElseThrow().active());
            assertEquals(1, categories.findById(category.id()).orElseThrow().version());
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private String columnType(String columnName) {
        return jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() AND table_name = 'items' AND column_name = ?",
                String.class,
                columnName);
    }

    private int constraintCount(String name, String type) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint "
                        + "WHERE conrelid = 'items'::regclass AND conname = ? AND contype = ?",
                Integer.class,
                name,
                type);
    }

    private int constraintCountForType(String type) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conrelid = 'items'::regclass AND contype = ?",
                Integer.class,
                type);
    }

    private ItemCategory insertCategory(String name, boolean active) {
        ItemCategory category = ItemCategory.reconstitute(UUID.randomUUID(), name, active, 0);
        return categories.insert(category);
    }

    private Optional<Boolean> lockInTransaction(UUID categoryId) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> categoryAssignmentLock.lockAndReadActive(categoryId));
    }

    private int currentBackendPid() {
        return jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }

    private boolean awaitDatabaseBlocking(int blocker, int waiter) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbcTemplate.queryForObject(
                    "SELECT CASE WHEN ? = ANY(pg_blocking_pids(pid)) THEN 1 ELSE 0 END "
                            + "FROM pg_stat_activity WHERE pid = ?",
                    Integer.class,
                    blocker,
                    waiter);
            if (blocked != null && blocked == 1) {
                return true;
            }
            Thread.yield();
        }
        return false;
    }

    private static void await(CountDownLatch latch, String description) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for " + description);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for " + description, exception);
        }
    }
}
