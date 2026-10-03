package dev.akume.storage.location.application.port.out;

import java.util.function.Supplier;

/** Executes one complete operation in a fresh SERIALIZABLE transaction. */
public interface SerializableTransactionExecutor {

    <T> T execute(Supplier<T> operation);
}
