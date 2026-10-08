package dev.akume.storage.catalog.application.service;

import dev.akume.storage.catalog.application.exception.InactiveItemCategoryException;
import dev.akume.storage.catalog.application.exception.ItemCategoryNotFoundException;
import dev.akume.storage.catalog.application.exception.ItemConcurrentModificationException;
import dev.akume.storage.catalog.application.model.ItemReadView;
import dev.akume.storage.catalog.application.port.in.ActivateItemCommand;
import dev.akume.storage.catalog.application.port.in.ActivateItemUseCase;
import dev.akume.storage.catalog.application.port.in.CreateItemCommand;
import dev.akume.storage.catalog.application.port.in.CreateItemUseCase;
import dev.akume.storage.catalog.application.port.in.DeactivateItemCommand;
import dev.akume.storage.catalog.application.port.in.DeactivateItemUseCase;
import dev.akume.storage.catalog.application.port.in.DeactivateItemCategoryCommand;
import dev.akume.storage.catalog.application.port.in.DeactivateItemCategoryUseCase;
import dev.akume.storage.catalog.application.port.in.GetItemUseCase;
import dev.akume.storage.catalog.application.port.in.ListItemsUseCase;
import dev.akume.storage.catalog.application.port.in.ReassignItemCategoryCommand;
import dev.akume.storage.catalog.application.port.in.ReassignItemCategoryUseCase;
import dev.akume.storage.catalog.application.port.in.UpdateItemMetadataCommand;
import dev.akume.storage.catalog.application.port.in.UpdateItemMetadataUseCase;
import dev.akume.storage.catalog.application.port.out.ItemCategoryAssignmentLock;
import dev.akume.storage.catalog.application.port.out.ItemCategoryRepository;
import dev.akume.storage.catalog.application.port.out.ItemRepository;
import dev.akume.storage.catalog.domain.model.Item;
import dev.akume.storage.catalog.domain.model.ItemCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ItemApplicationIntegrationTest {

    @Autowired
    private CreateItemUseCase createItems;

    @Autowired
    private GetItemUseCase getItems;

    @Autowired
    private ListItemsUseCase listItems;

    @Autowired
    private UpdateItemMetadataUseCase updateMetadata;

    @Autowired
    private ReassignItemCategoryUseCase reassignCategory;

    @Autowired
    private ActivateItemUseCase activateItems;

    @Autowired
    private DeactivateItemUseCase deactivateItems;

    @Autowired
    private DeactivateItemCategoryUseCase deactivateCategories;

    @Autowired
    private ItemRepository items;

    @Autowired
    private ItemCategoryRepository categories;

    @Autowired
    private ItemCategoryAssignmentLock categoryLock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    @AfterEach
    void clearItemsAndCategories() {
        jdbcTemplate.update("DELETE FROM items");
        jdbcTemplate.update("DELETE FROM item_categories");
    }

    @Test
    void createsItemWithActiveCategoryAndPersistsVersionZero() {
        ItemCategory category = insertCategory("Hardware", true);

        ItemReadView created = createItems.create(new CreateItemCommand("  Board  ", "  Dev kit  ", category.id()));

        Item persisted = items.findById(created.id()).orElseThrow();
        assertEquals(created.id(), persisted.id());
        assertEquals("Board", persisted.name());
        assertEquals("Dev kit", persisted.description());
        assertEquals(category.id(), persisted.itemCategoryId());
        assertTrue(persisted.active());
        assertEquals(0, persisted.version());
    }

    @Test
    void inactiveAndMissingCategoriesRejectCreationWithoutPersistingItem() {
        ItemCategory inactive = insertCategory("Inactive", false);
        assertThrows(InactiveItemCategoryException.class,
                () -> createItems.create(new CreateItemCommand("Board", null, inactive.id())));
        assertThrows(ItemCategoryNotFoundException.class,
                () -> createItems.create(new CreateItemCommand("Board", null, UUID.randomUUID())));
        assertTrue(items.findAll().isEmpty());
    }

    @Test
    void metadataUpdateDoesNotReassignCategoryAndReturnsPersistedVersion() {
        ItemCategory category = insertCategory("Hardware", true);
        ItemReadView created = createItems.create(new CreateItemCommand("Board", null, category.id()));

        ItemReadView updated = updateMetadata.updateMetadata(
                new UpdateItemMetadataCommand(created.id(), " Development board ", " S3 family ", 0));

        assertEquals(created.id(), updated.id());
        assertEquals(category.id(), updated.itemCategoryId());
        assertEquals("Development board", updated.name());
        assertEquals("S3 family", updated.description());
        assertEquals(1, updated.version());
    }

    @Test
    void reassignmentRequiresActiveTargetAndPreservesItemWhenTargetIsInactive() {
        ItemCategory source = insertCategory("Source", true);
        ItemCategory target = insertCategory("Target", true);
        ItemReadView created = createItems.create(new CreateItemCommand("Board", "Prototype", source.id()));
        ItemCategory inactive = deactivateCategories.deactivate(
                new DeactivateItemCategoryCommand(target.id(), target.version()));

        assertFalse(inactive.active());
        assertThrows(InactiveItemCategoryException.class, () -> reassignCategory.reassignCategory(
                new ReassignItemCategoryCommand(created.id(), target.id(), created.version())));

        Item unchanged = items.findById(created.id()).orElseThrow();
        assertEquals(source.id(), unchanged.itemCategoryId());
        assertEquals(created.name(), unchanged.name());
        assertEquals(created.active(), unchanged.active());
        assertEquals(created.version(), unchanged.version());
    }

    @Test
    void lifecycleIsIdempotentChecksVersionFirstAndIgnoresInactiveExistingCategory() {
        ItemCategory category = insertCategory("Hardware", true);
        ItemReadView created = createItems.create(new CreateItemCommand("Board", null, category.id()));
        ItemReadView inactive = deactivateItems.deactivate(new DeactivateItemCommand(created.id(), 0));
        ItemCategory nowInactive = deactivateCategories.deactivate(
                new DeactivateItemCategoryCommand(category.id(), category.version()));

        ItemReadView noOp = deactivateItems.deactivate(new DeactivateItemCommand(inactive.id(), inactive.version()));
        ItemReadView reactivated = activateItems.activate(new ActivateItemCommand(inactive.id(), inactive.version()));
        ItemReadView lifecycleNoOp = activateItems.activate(new ActivateItemCommand(reactivated.id(), reactivated.version()));

        assertFalse(nowInactive.active());
        assertFalse(noOp.active());
        assertEquals(1, noOp.version());
        assertTrue(reactivated.active());
        assertEquals(2, reactivated.version());
        assertTrue(lifecycleNoOp.active());
        assertEquals(2, lifecycleNoOp.version());
        assertEquals(category.id(), items.findById(created.id()).orElseThrow().itemCategoryId());
        assertThrows(ItemConcurrentModificationException.class,
                () -> activateItems.activate(new ActivateItemCommand(inactive.id(), 0)));
    }

    @Test
    void optimisticItemUpdatesRejectStaleVersionWithoutOverwritingState() {
        ItemCategory category = insertCategory("Hardware", true);
        ItemReadView created = createItems.create(new CreateItemCommand("Board", null, category.id()));
        ItemReadView current = updateMetadata.updateMetadata(
                new UpdateItemMetadataCommand(created.id(), "Current", null, created.version()));

        assertThrows(ItemConcurrentModificationException.class, () -> updateMetadata.updateMetadata(
                new UpdateItemMetadataCommand(created.id(), "Stale", "stale", created.version())));

        ItemReadView persisted = getItems.getById(created.id());
        assertEquals("Current", persisted.name());
        assertEquals(current.version(), persisted.version());
        assertNotEquals("Stale", persisted.name());
    }

    @Test
    void concurrentStaleItemUpdatesAllowOneCommitAndRejectTheOther() throws Exception {
        ItemCategory category = insertCategory("Hardware", true);
        ItemReadView created = createItems.create(new CreateItemCommand("Board", null, category.id()));
        CountDownLatch firstUpdatePrepared = new CountDownLatch(1);
        CountDownLatch firstUpdateMayCommit = new CountDownLatch(1);
        CountDownLatch staleUpdateStarted = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger stalePid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Future<ItemReadView> winningUpdate = null;
        Future<ItemReadView> staleUpdate = null;

        try {
            winningUpdate = executor.submit(() -> transaction.execute(status -> {
                firstPid.set(currentBackendPid());
                jdbcTemplate.queryForObject(
                        "SELECT id FROM items WHERE id = ? FOR UPDATE", UUID.class, created.id());
                ItemReadView updated = updateMetadata.updateMetadata(
                        new UpdateItemMetadataCommand(created.id(), "Winner", null, created.version()));
                firstUpdatePrepared.countDown();
                await(firstUpdateMayCommit, "winning Item update commit");
                return updated;
            }));
            assertTrue(firstUpdatePrepared.await(10, TimeUnit.SECONDS), "first update must be prepared");

            staleUpdate = executor.submit(() -> transaction.execute(status -> {
                stalePid.set(currentBackendPid());
                staleUpdateStarted.countDown();
                return updateMetadata.updateMetadata(
                        new UpdateItemMetadataCommand(created.id(), "Loser", "stale", created.version()));
            }));
            assertTrue(staleUpdateStarted.await(10, TimeUnit.SECONDS), "stale update must start");
            assertTrue(awaitDatabaseBlocking(firstPid.get(), stalePid.get()),
                    "stale update must block on the first transaction's Item row write");
            assertFalse(staleUpdate.isDone(), "stale update must wait for the winning transaction");

            firstUpdateMayCommit.countDown();
            ItemReadView winner = winningUpdate.get(10, TimeUnit.SECONDS);
            Future<ItemReadView> staleResult = staleUpdate;
            ExecutionException rejected = assertThrows(ExecutionException.class,
                    () -> staleResult.get(10, TimeUnit.SECONDS));
            assertTrue(rejected.getCause() instanceof ItemConcurrentModificationException);
            Item persisted = items.findById(created.id()).orElseThrow();
            assertEquals("Winner", winner.name());
            assertEquals("Winner", persisted.name());
            assertEquals(1, persisted.version());
        } finally {
            firstUpdateMayCommit.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void transactionRollbackRemovesCreatedItemAndReleasesAssignmentLock() {
        ItemCategory category = insertCategory("Hardware", true);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        UUID[] createdId = new UUID[1];

        assertThrows(DeliberateRollback.class, () -> transaction.executeWithoutResult(status -> {
            ItemReadView created = createItems.create(new CreateItemCommand("Rolled back", null, category.id()));
            createdId[0] = created.id();
            throw new DeliberateRollback();
        }));

        assertTrue(items.findById(createdId[0]).isEmpty());
        assertEquals(Optional.of(true), lockInTransaction(category.id()));
    }

    @Test
    void assignmentFirstSerializesBeforeDeactivationAndLeavesValidExistingReference() throws Exception {
        ItemCategory category = insertCategory("Create race", true);

        RaceResult<ItemReadView> race = assignmentFirst(category, () -> createItems.create(
                new CreateItemCommand("Created before deactivate", null, category.id())));

        assertNotNull(race.assignmentResult());
        assertFalse(race.deactivationFailure() != null);
        Item persisted = items.findById(race.assignmentResult().id()).orElseThrow();
        assertEquals(category.id(), persisted.itemCategoryId());
        assertFalse(categories.findById(category.id()).orElseThrow().active());
    }

    @Test
    void deactivationFirstMakesConcurrentCreateRejectInactiveCategory() throws Exception {
        ItemCategory category = insertCategory("Create race", true);

        RaceResult<ItemReadView> race = deactivationFirst(category, () -> createItems.create(
                new CreateItemCommand("Rejected after deactivate", null, category.id())));

        assertTrue(race.assignmentFailure() instanceof InactiveItemCategoryException);
        assertTrue(race.deactivationFailure() == null);
        assertTrue(items.findAll().isEmpty());
        assertFalse(categories.findById(category.id()).orElseThrow().active());
    }

    @Test
    void reassignmentFirstSerializesBeforeTargetDeactivationAndRetainsReference() throws Exception {
        ItemCategory source = insertCategory("Source", true);
        ItemCategory target = insertCategory("Reassign target", true);
        ItemReadView created = createItems.create(new CreateItemCommand("Reassigned", "metadata", source.id()));

        RaceResult<ItemReadView> race = assignmentFirst(target, () -> reassignCategory.reassignCategory(
                new ReassignItemCategoryCommand(created.id(), target.id(), created.version())));

        assertNotNull(race.assignmentResult());
        assertTrue(race.deactivationFailure() == null);
        Item persisted = items.findById(created.id()).orElseThrow();
        assertEquals(target.id(), persisted.itemCategoryId());
        assertEquals("Reassigned", persisted.name());
        assertEquals("metadata", persisted.description());
        assertFalse(categories.findById(target.id()).orElseThrow().active());
    }

    @Test
    void deactivationFirstMakesConcurrentReassignmentRejectAndPreserveItem() throws Exception {
        ItemCategory source = insertCategory("Source", true);
        ItemCategory target = insertCategory("Reassign target", true);
        ItemReadView created = createItems.create(new CreateItemCommand("Unchanged", "details", source.id()));

        RaceResult<ItemReadView> race = deactivationFirst(target, () -> reassignCategory.reassignCategory(
                new ReassignItemCategoryCommand(created.id(), target.id(), created.version())));

        assertTrue(race.assignmentFailure() instanceof InactiveItemCategoryException);
        assertTrue(race.deactivationFailure() == null);
        Item persisted = items.findById(created.id()).orElseThrow();
        assertEquals(source.id(), persisted.itemCategoryId());
        assertEquals(created.name(), persisted.name());
        assertEquals(created.description(), persisted.description());
        assertEquals(created.active(), persisted.active());
        assertEquals(created.version(), persisted.version());
    }

    @Test
    void listReturnsActiveAndInactiveItems() {
        ItemCategory category = insertCategory("Hardware", true);
        ItemReadView active = createItems.create(new CreateItemCommand("Active", null, category.id()));
        ItemReadView inactive = deactivateItems.deactivate(new DeactivateItemCommand(active.id(), active.version()));
        ItemReadView second = createItems.create(new CreateItemCommand("Another", null, category.id()));

        List<ItemReadView> listed = listItems.listAll();

        assertEquals(2, listed.size());
        assertTrue(listed.stream().anyMatch(item -> item.id().equals(inactive.id()) && !item.active()));
        assertTrue(listed.stream().anyMatch(item -> item.id().equals(second.id()) && item.active()));
    }

    private RaceResult<ItemReadView> assignmentFirst(ItemCategory category, AssignmentAction assignment) throws Exception {
        CountDownLatch assignmentLockHeld = new CountDownLatch(1);
        CountDownLatch assignmentMayProceed = new CountDownLatch(1);
        CountDownLatch deactivationStarted = new CountDownLatch(1);
        AtomicInteger assignmentPid = new AtomicInteger();
        AtomicInteger deactivationPid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Future<ItemReadView> assignmentFuture = null;
        Future<ItemCategory> deactivationFuture = null;

        try {
            assignmentFuture = executor.submit(() -> transaction.execute(status -> {
                assignmentPid.set(currentBackendPid());
                assertEquals(Optional.of(true), categoryLock.lockAndReadActive(category.id()));
                assignmentLockHeld.countDown();
                await(assignmentMayProceed, "assignment continuation");
                return assignment.run();
            }));
            assertTrue(assignmentLockHeld.await(10, TimeUnit.SECONDS), "assignment must hold target lock");

            deactivationFuture = executor.submit(() -> transaction.execute(status -> {
                deactivationPid.set(currentBackendPid());
                deactivationStarted.countDown();
                return deactivateCategories.deactivate(
                        new DeactivateItemCategoryCommand(category.id(), category.version()));
            }));
            assertTrue(deactivationStarted.await(10, TimeUnit.SECONDS), "deactivation must start");
            assertTrue(awaitDatabaseBlocking(assignmentPid.get(), deactivationPid.get()),
                    "category deactivation must block behind assignment row lock");
            assertFalse(deactivationFuture.isDone(), "deactivation must wait for assignment transaction");

            assignmentMayProceed.countDown();
            ItemReadView assigned = assignmentFuture.get(10, TimeUnit.SECONDS);
            ItemCategory deactivated = deactivationFuture.get(10, TimeUnit.SECONDS);
            assertFalse(deactivated.active());
            return new RaceResult<>(assigned, null, deactivated, null);
        } catch (ExecutionException exception) {
            return new RaceResult<>(null, exception.getCause(), null, exception.getCause());
        } finally {
            assignmentMayProceed.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private RaceResult<ItemReadView> deactivationFirst(ItemCategory category, AssignmentAction assignment) throws Exception {
        CountDownLatch deactivationUpdated = new CountDownLatch(1);
        CountDownLatch deactivationMayCommit = new CountDownLatch(1);
        CountDownLatch assignmentStarted = new CountDownLatch(1);
        AtomicInteger deactivationPid = new AtomicInteger();
        AtomicInteger assignmentPid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Future<ItemCategory> deactivationFuture = null;
        Future<ItemReadView> assignmentFuture = null;

        try {
            deactivationFuture = executor.submit(() -> transaction.execute(status -> {
                deactivationPid.set(currentBackendPid());
                ItemCategory updated = deactivateCategories.deactivate(
                        new DeactivateItemCategoryCommand(category.id(), category.version()));
                deactivationUpdated.countDown();
                await(deactivationMayCommit, "deactivation commit");
                return updated;
            }));
            assertTrue(deactivationUpdated.await(10, TimeUnit.SECONDS), "deactivation must update row");

            assignmentFuture = executor.submit(() -> transaction.execute(status -> {
                assignmentPid.set(currentBackendPid());
                assignmentStarted.countDown();
                return assignment.run();
            }));
            assertTrue(assignmentStarted.await(10, TimeUnit.SECONDS), "assignment must start");
            assertTrue(awaitDatabaseBlocking(deactivationPid.get(), assignmentPid.get()),
                    "assignment lock query must wait behind category deactivation");
            assertFalse(assignmentFuture.isDone(), "assignment must wait for category transaction");

            deactivationMayCommit.countDown();
            ItemCategory deactivated = deactivationFuture.get(10, TimeUnit.SECONDS);
            try {
                ItemReadView assigned = assignmentFuture.get(10, TimeUnit.SECONDS);
                return new RaceResult<>(assigned, null, deactivated, null);
            } catch (ExecutionException exception) {
                return new RaceResult<>(null, exception.getCause(), deactivated, null);
            }
        } finally {
            deactivationMayCommit.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private ItemCategory insertCategory(String name, boolean active) {
        return categories.insert(ItemCategory.reconstitute(UUID.randomUUID(), name, active, 0));
    }

    private Optional<Boolean> lockInTransaction(UUID id) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> categoryLock.lockAndReadActive(id));
    }

    private int currentBackendPid() {
        return jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }

    private boolean awaitDatabaseBlocking(int blockerPid, int waiterPid) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbcTemplate.queryForObject(
                    "SELECT CASE WHEN ? = ANY(pg_blocking_pids(pid)) THEN 1 ELSE 0 END "
                            + "FROM pg_stat_activity WHERE pid = ?",
                    Integer.class,
                    blockerPid,
                    waiterPid);
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

    @FunctionalInterface
    private interface AssignmentAction {
        ItemReadView run();
    }

    private record RaceResult<T>(
            T assignmentResult,
            Throwable assignmentFailure,
            ItemCategory deactivationResult,
            Throwable deactivationFailure) {
    }

    private static final class DeliberateRollback extends RuntimeException {
    }
}
