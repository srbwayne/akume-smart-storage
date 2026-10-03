package dev.akume.storage.location.application.exception;

/** Indicates that an operation exhausted its bounded serialization-failure retries. */
public class SerializableOperationConflictException extends RuntimeException {

    public SerializableOperationConflictException(Throwable cause) {
        super("Operation could not complete after serialization retries", cause);
    }
}
