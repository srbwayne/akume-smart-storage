package dev.akume.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AkumeStorageApplicationTest {

    @Test
    void foundationTestBaselineRuns() {
        assertTrue(AkumeStorageApplication.class.getPackageName().startsWith("dev.akume.storage"));
    }
}
