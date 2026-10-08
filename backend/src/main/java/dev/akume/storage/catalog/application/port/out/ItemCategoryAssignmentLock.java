package dev.akume.storage.catalog.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Acquires the target category row lock and returns its active state in the caller's transaction. */
public interface ItemCategoryAssignmentLock {

    /**
     * Locks the category row until the owning transaction completes.
     * Empty means the category does not exist; true/false reports its state after lock acquisition.
     */
    Optional<Boolean> lockAndReadActive(UUID itemCategoryId);
}
