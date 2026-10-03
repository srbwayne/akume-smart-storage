package dev.akume.storage.location.adapter.out.transaction;

import dev.akume.storage.location.application.exception.SerializableOperationConflictException;
import dev.akume.storage.location.application.port.out.SerializableTransactionExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Runs complete operation attempts in independent PostgreSQL SERIALIZABLE transactions. */
@Component
public class SpringSerializableTransactionExecutor implements SerializableTransactionExecutor {

    private static final int MAX_ATTEMPTS = 3;
    private static final String SERIALIZATION_FAILURE_SQL_STATE = "40001";

    private final TransactionTemplate transactionTemplate;

    public SpringSerializableTransactionExecutor(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    @Override
    public <T> T execute(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation must not be null");

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                // The retry boundary surrounds execute itself so callback, flush, and commit
                // failures are all classified after this transaction has completed/rolled back.
                return transactionTemplate.execute(status -> operation.get());
            } catch (RuntimeException failure) {
                if (!hasSerializationFailureSqlState(failure)) {
                    throw failure;
                }
                if (attempt == MAX_ATTEMPTS) {
                    throw new SerializableOperationConflictException(failure);
                }
            }
        }

        throw new IllegalStateException("unreachable retry state");
    }

    private static boolean hasSerializationFailureSqlState(Throwable failure) {
        if (failure == null) {
            return false;
        }

        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(failure);

        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }

            if (current instanceof SQLException sqlException) {
                if (SERIALIZATION_FAILURE_SQL_STATE.equals(sqlException.getSQLState())) {
                    return true;
                }
                SQLException next = sqlException.getNextException();
                if (next != null && !visited.contains(next)) {
                    pending.addLast(next);
                }
            }

            Throwable cause = current.getCause();
            if (cause != null && !visited.contains(cause)) {
                pending.addLast(cause);
            }
        }

        return false;
    }
}
