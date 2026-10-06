package dev.akume.storage.catalog.adapter.out.persistence;

import dev.akume.storage.catalog.application.exception.ItemCategoryConcurrentModificationException;
import dev.akume.storage.catalog.application.exception.ItemCategoryNameAlreadyExistsException;
import dev.akume.storage.catalog.application.port.out.ItemCategoryRepository;
import dev.akume.storage.catalog.domain.model.ItemCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ItemCategoryPersistenceIntegrationTest {

    @Autowired
    private ItemCategoryRepository categories;

    @Autowired
    private SpringDataItemCategoryRepository jpaCategories;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearCategories() {
        jpaCategories.deleteAll();
    }

    @Test
    void savesAndReloadsAllPersistedValuesThroughDomainMapping() {
        UUID id = UUID.randomUUID();
        ItemCategory source = ItemCategory.reconstitute(id, "Café", false, 0);

        ItemCategory inserted = categories.insert(source);
        ItemCategory reloaded = categories.findById(id).orElseThrow();

        assertEquals(0, inserted.version());
        assertEquals(id, reloaded.id());
        assertEquals("Café", reloaded.name());
        assertEquals("café", reloaded.canonicalNameKey());
        assertFalse(reloaded.active());
        assertEquals(0, reloaded.version());
        assertEquals(1, categories.findAll().size());
    }

    @Test
    void persistsNewCategoriesAtVersionZero() {
        ItemCategory inserted = categories.insert(ItemCategory.create("Cable"));

        assertEquals(0, inserted.version());
        assertEquals(0, categories.findById(inserted.id()).orElseThrow().version());
    }

    @Test
    void databaseRejectsDuplicateCanonicalNameKeyAndAdapterTranslatesIt() {
        categories.insert(ItemCategory.create("Cable"));

        ItemCategoryNameAlreadyExistsException duplicate = assertThrows(
                ItemCategoryNameAlreadyExistsException.class,
                () -> categories.insert(ItemCategory.create("cable")));

        assertTrue(duplicate.getMessage().contains("cable"));
        assertEquals(1, categories.findAll().size());
    }

    @Test
    void composedAndDecomposedEquivalentNamesConflictAfterNfcNormalization() {
        String decomposed = Normalizer.normalize("Café", Normalizer.Form.NFD);
        categories.insert(ItemCategory.create("Café"));

        assertThrows(ItemCategoryNameAlreadyExistsException.class,
                () -> categories.insert(ItemCategory.create(decomposed)));
        assertEquals("Café", categories.findAll().getFirst().name());
    }

    @Test
    void inactiveCategoryContinuesToReserveItsCanonicalName() {
        categories.insert(ItemCategory.reconstitute(UUID.randomUUID(), "Cable", false, 0));

        assertThrows(ItemCategoryNameAlreadyExistsException.class,
                () -> categories.insert(ItemCategory.create("CABLE")));
        assertFalse(categories.findAll().getFirst().active());
    }

    @Test
    void accentAndInternalWhitespaceDifferencesRemainDistinct() {
        categories.insert(ItemCategory.create("Armário"));
        categories.insert(ItemCategory.create("armario"));
        categories.insert(ItemCategory.create("Sala  Principal"));
        categories.insert(ItemCategory.create("Sala Principal"));

        assertEquals(4, categories.findAll().size());
    }

    @Test
    void unrelatedPrimaryKeyConstraintIsNotTranslatedAsDuplicateName() {
        UUID duplicateId = UUID.randomUUID();
        categories.insert(ItemCategory.reconstitute(duplicateId, "Cable", true, 0));

        DataIntegrityViolationException failure = assertThrows(
                DataIntegrityViolationException.class,
                () -> categories.insert(ItemCategory.reconstitute(duplicateId, "Power", true, 0)));

        assertNotNull(failure);
        assertEquals(1, categories.findAll().size());
    }

    @Test
    void databaseRejectsNegativeVersion() {
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO item_categories (id, name, normalized_name_key, active, version) "
                                + "VALUES (?, ?, ?, ?, ?)",
                        UUID.randomUUID(), "Invalid", "invalid", true, -1));
    }

    @Test
    void schemaHasNoItemForeignKey() {
        Integer foreignKeyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint "
                        + "WHERE conrelid = 'item_categories'::regclass AND contype = 'f'",
                Integer.class);

        assertEquals(0, foreignKeyCount);
    }

    @Test
    void updatesStateWithProviderManagedVersionAndRejectsStaleSnapshot() {
        ItemCategory inserted = categories.insert(ItemCategory.create("Cable"));
        ItemCategory staleSnapshot = categories.findById(inserted.id()).orElseThrow();
        ItemCategory changedSnapshot = ItemCategory.reconstitute(
                inserted.id(), "Power", true, inserted.version());

        ItemCategory updated = categories.update(changedSnapshot).orElseThrow();

        assertEquals(1, updated.version());
        assertEquals("Power", updated.name());
        assertEquals(1, categories.findById(inserted.id()).orElseThrow().version());
        assertThrows(ItemCategoryConcurrentModificationException.class,
                () -> categories.update(staleSnapshot));
        assertEquals("Power", categories.findById(inserted.id()).orElseThrow().name());
    }

    @Test
    void translatesCanonicalNameConflictDuringUpdateAndPreservesStoredName() {
        ItemCategory cable = categories.insert(ItemCategory.create("Cable"));
        categories.insert(ItemCategory.create("Power"));
        ItemCategory duplicateRename = ItemCategory.reconstitute(
                cable.id(), "Power", true, cable.version());

        assertThrows(ItemCategoryNameAlreadyExistsException.class,
                () -> categories.update(duplicateRename));
        assertEquals("Cable", categories.findById(cable.id()).orElseThrow().name());
    }

    @Test
    void updateReturnsEmptyWhenCategoryDoesNotExist() {
        ItemCategory missing = ItemCategory.create("Missing");

        assertTrue(categories.update(missing).isEmpty());
    }

    @Test
    void concurrentCanonicalDuplicateInsertionsCannotBothCommit() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> outcomes = new ArrayList<>();
            outcomes.add(executor.submit(() -> attemptInsert("Race Category", ready, start)));
            outcomes.add(executor.submit(() -> attemptInsert("race category", ready, start)));

            assertTrue(ready.await(10, TimeUnit.SECONDS), "both writers should be ready");
            start.countDown();

            long successfulInserts = 0;
            long duplicateConflicts = 0;
            for (Future<Boolean> outcome : outcomes) {
                if (outcome.get(20, TimeUnit.SECONDS)) {
                    successfulInserts++;
                } else {
                    duplicateConflicts++;
                }
            }

            assertEquals(1, successfulInserts);
            assertEquals(1, duplicateConflicts);
            assertEquals(1, categories.findAll().size());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private boolean attemptInsert(String name, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.executeWithoutResult(status -> {
                // Acquire a PostgreSQL connection and keep both transactions open at the barrier.
                jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);
                ready.countDown();
                try {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("insert start was not released");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("insert was interrupted", exception);
                }
                categories.insert(ItemCategory.create(name));
            });
            return true;
        } catch (ItemCategoryNameAlreadyExistsException expected) {
            return false;
        }
    }
}
