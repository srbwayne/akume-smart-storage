package dev.akume.storage.location.adapter.out.transaction;

import dev.akume.storage.location.application.exception.SerializableOperationConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringSerializableTransactionExecutorTest {

    @Test
    void successfulOperationRunsOnceAndReturnsItsResult() {
        PlatformTransactionManager transactions = transactionManager();
        SpringSerializableTransactionExecutor executor = new SpringSerializableTransactionExecutor(transactions);
        AtomicInteger calls = new AtomicInteger();

        String result = executor.execute(() -> {
            calls.incrementAndGet();
            return "done";
        });

        assertEquals("done", result);
        assertEquals(1, calls.get());
        verify(transactions).commit(any(TransactionStatus.class));
    }

    @Test
    void retriesOnceAfterStructuredSerializationFailure() {
        SpringSerializableTransactionExecutor executor = new SpringSerializableTransactionExecutor(
                transactionManager());
        AtomicInteger calls = new AtomicInteger();

        String result = executor.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw wrappedSqlState("40001");
            }
            return "second attempt";
        });

        assertEquals("second attempt", result);
        assertEquals(2, calls.get());
    }

    @Test
    void retriesTwiceThenReturnsThirdAttemptResult() {
        SpringSerializableTransactionExecutor executor = new SpringSerializableTransactionExecutor(
                transactionManager());
        AtomicInteger calls = new AtomicInteger();

        String result = executor.execute(() -> {
            if (calls.incrementAndGet() < 3) {
                throw wrappedSqlState("40001");
            }
            return "third attempt";
        });

        assertEquals("third attempt", result);
        assertEquals(3, calls.get());
    }

    @Test
    void exhaustionStopsAfterThreeAttemptsAndRetainsFinalFailure() {
        SpringSerializableTransactionExecutor executor = new SpringSerializableTransactionExecutor(
                transactionManager());
        AtomicInteger calls = new AtomicInteger();
        RuntimeException thirdFailure = wrappedSqlState("40001");

        SerializableOperationConflictException failure = assertThrows(
                SerializableOperationConflictException.class,
                () -> executor.execute(() -> {
                    if (calls.incrementAndGet() == 3) {
                        throw thirdFailure;
                    }
                    throw wrappedSqlState("40001");
                }));

        assertEquals(3, calls.get());
        assertSame(thirdFailure, failure.getCause());
    }

    @Test
    void nonSerializationSqlStateIsPropagatedWithoutRetry() {
        SpringSerializableTransactionExecutor executor = new SpringSerializableTransactionExecutor(
                transactionManager());
        AtomicInteger calls = new AtomicInteger();
        RuntimeException failure = wrappedSqlState("23505");

        RuntimeException result = assertThrows(RuntimeException.class, () -> executor.execute(() -> {
            calls.incrementAndGet();
            throw failure;
        }));

        assertSame(failure, result);
        assertEquals(1, calls.get());
    }

    @Test
    void unrelatedRuntimeFailureIsPropagatedWithoutRetry() {
        SpringSerializableTransactionExecutor executor = new SpringSerializableTransactionExecutor(
                transactionManager());
        AtomicInteger calls = new AtomicInteger();
        RuntimeException failure = new IllegalStateException("unrelated");

        RuntimeException result = assertThrows(RuntimeException.class, () -> executor.execute(() -> {
            calls.incrementAndGet();
            throw failure;
        }));

        assertSame(failure, result);
        assertEquals(1, calls.get());
    }

    @Test
    void sqlStateInMessageWithoutStructuredStateDoesNotRetry() {
        SpringSerializableTransactionExecutor executor = new SpringSerializableTransactionExecutor(
                transactionManager());
        AtomicInteger calls = new AtomicInteger();
        RuntimeException failure = new IllegalStateException("database reported 40001");

        RuntimeException result = assertThrows(RuntimeException.class, () -> executor.execute(() -> {
            calls.incrementAndGet();
            throw failure;
        }));

        assertSame(failure, result);
        assertEquals(1, calls.get());
    }

    @Test
    void commitTimeSerializationFailureIsCaughtByOuterRetryBoundary() {
        PlatformTransactionManager transactions = transactionManager();
        TransactionStatus firstStatus = transactionStatus();
        TransactionStatus secondStatus = transactionStatus();
        when(transactions.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(firstStatus, secondStatus);
        RuntimeException commitFailure = wrappedSqlState("40001");
        AtomicInteger commits = new AtomicInteger();
        doAnswer(invocation -> {
            if (commits.incrementAndGet() == 1) {
                assertSame(firstStatus, invocation.getArgument(0));
                throw commitFailure;
            }
            assertSame(secondStatus, invocation.getArgument(0));
            return null;
        }).when(transactions).commit(any(TransactionStatus.class));

        SpringSerializableTransactionExecutor executor = new SpringSerializableTransactionExecutor(transactions);
        AtomicInteger calls = new AtomicInteger();

        String result = executor.execute(() -> "result-" + calls.incrementAndGet());

        assertEquals("result-2", result);
        assertEquals(2, calls.get());
        assertEquals(2, commits.get());
        assertNotSame(firstStatus, secondStatus);
        ArgumentCaptor<TransactionDefinition> definitions = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactions, times(2)).getTransaction(definitions.capture());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                definitions.getAllValues().get(0).getPropagationBehavior());
        assertEquals(TransactionDefinition.ISOLATION_SERIALIZABLE,
                definitions.getAllValues().get(0).getIsolationLevel());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                definitions.getAllValues().get(1).getPropagationBehavior());
        assertEquals(TransactionDefinition.ISOLATION_SERIALIZABLE,
                definitions.getAllValues().get(1).getIsolationLevel());
        verify(transactions, times(1)).commit(firstStatus);
        verify(transactions, times(1)).commit(secondStatus);
        assertEquals("40001", sqlException(commitFailure).getSQLState());
    }

    @Test
    void nullOperationIsRejectedBeforeOpeningATransaction() {
        PlatformTransactionManager transactions = transactionManager();
        SpringSerializableTransactionExecutor executor = new SpringSerializableTransactionExecutor(transactions);

        NullPointerException failure = assertThrows(NullPointerException.class, () -> executor.execute(null));

        assertEquals("operation must not be null", failure.getMessage());
        verify(transactions, times(0)).getTransaction(any(TransactionDefinition.class));
    }

    private static PlatformTransactionManager transactionManager() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus status = transactionStatus();
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        return manager;
    }

    private static TransactionStatus transactionStatus() {
        TransactionStatus status = mock(TransactionStatus.class);
        when(status.isNewTransaction()).thenReturn(true);
        return status;
    }

    private static RuntimeException wrappedSqlState(String state) {
        return new IllegalStateException("wrapped", new RuntimeException("jdbc", new SQLException("failure", state)));
    }

    private static SQLException sqlException(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                return sqlException;
            }
        }
        throw new AssertionError("structured SQLException not found", failure);
    }
}
