package com.pythonburp.concurrency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RuntimeActivityCoordinatorTest {
    @Test
    void packageMutationIsRejectedWhileScriptIsActive() {
        RuntimeActivityCoordinator coordinator = new RuntimeActivityCoordinator();

        try (var ignored = coordinator.beginScript()) {
            assertThrows(IllegalStateException.class, coordinator::beginPackageMutation);
        }
    }

    @Test
    void scriptIsRejectedWhilePackageMutationIsActive() {
        RuntimeActivityCoordinator coordinator = new RuntimeActivityCoordinator();

        try (var ignored = coordinator.beginPackageMutation()) {
            assertThrows(IllegalStateException.class, coordinator::beginScript);
        }
    }

    @Test
    void multipleScriptsCanRunTogether() {
        RuntimeActivityCoordinator coordinator = new RuntimeActivityCoordinator();

        try (var first = coordinator.beginScript(); var second = coordinator.beginScript()) {
            assertEquals(2, coordinator.snapshot().activeScripts());
        }
        assertEquals(0, coordinator.snapshot().activeScripts());
    }
}
