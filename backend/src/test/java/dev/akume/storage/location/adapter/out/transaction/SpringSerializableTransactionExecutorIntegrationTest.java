package dev.akume.storage.location.adapter.out.transaction;

import dev.akume.storage.location.application.port.out.SerializableTransactionExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SpringSerializableTransactionExecutorIntegrationTest {

    @Autowired
    private SerializableTransactionExecutor executor;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void attemptRunsInActivePostgresSerializableTransaction() {
        TransactionFacts facts = executor.execute(this::transactionFacts);

        assertTrue(facts.active());
        assertEquals("serializable", facts.isolation());
        assertTrue(facts.transactionId() > 0);
    }

    @Test
    void retryUsesDifferentPhysicalPostgresTransaction() {
        List<Long> transactionIds = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();

        TransactionFacts result = executor.execute(() -> {
            TransactionFacts facts = transactionFacts();
            transactionIds.add(facts.transactionId());
            if (attempts.incrementAndGet() == 1) {
                // A structured 40001 drives the retry while the first database transaction
                // is rolled back. The unit test separately proves commit-time classification.
                throw new RuntimeException("simulated PostgreSQL serialization failure",
                        new SQLException("serialization failure", "40001"));
            }
            return facts;
        });

        assertEquals(2, attempts.get());
        assertEquals(2, transactionIds.size());
        assertNotEquals(transactionIds.get(0), transactionIds.get(1));
        assertEquals("serializable", result.isolation());
    }

    @Test
    void requiresNewAttemptIsIndependentOfAndRestoresAmbientTransaction() {
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        outer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        outer.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        outer.execute(status -> {
            long outerBefore = transactionId();
            String outerIsolation = isolation();

            TransactionFacts inner = executor.execute(this::transactionFacts);

            long outerAfter = transactionId();
            assertNotEquals(outerBefore, inner.transactionId());
            assertEquals(outerBefore, outerAfter);
            assertEquals("read committed", outerIsolation);
            assertEquals("serializable", inner.isolation());
            return null;
        });
    }

    private TransactionFacts transactionFacts() {
        return new TransactionFacts(
                org.springframework.transaction.support.TransactionSynchronizationManager
                        .isActualTransactionActive(),
                isolation(),
                transactionId());
    }

    private String isolation() {
        return jdbc.queryForObject("SHOW transaction_isolation", String.class);
    }

    private long transactionId() {
        return jdbc.queryForObject("SELECT txid_current()", Long.class);
    }

    private record TransactionFacts(boolean active, String isolation, long transactionId) {
    }
}
