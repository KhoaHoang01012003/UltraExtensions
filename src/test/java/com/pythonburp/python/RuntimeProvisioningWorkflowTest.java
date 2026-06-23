package com.pythonburp.python;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuntimeProvisioningWorkflowTest {
    @TempDir
    Path tempDir;

    @Test
    void returnsReadyWhenTargetDirectoryIsAlreadyWritable() {
        Path runtimeRoot = tempDir.resolve("Nmap/zenmap/bin/BurpPythonIDE");
        AtomicBoolean prompted = new AtomicBoolean(false);
        AtomicBoolean provisioned = new AtomicBoolean(false);

        RuntimeProvisioningOutcome outcome = new RuntimeProvisioningWorkflow(
            runtimeRoot,
            RuntimeProvisioningProbe.fileSystem(),
            (ignoredRoot, ignoredFailure) -> {
                prompted.set(true);
                return true;
            },
            ignoredRoot -> provisioned.set(true)
        ).ensureReady();

        assertTrue(outcome.ready());
        assertTrue(Files.isDirectory(runtimeRoot));
        assertFalse(prompted.get());
        assertFalse(provisioned.get());
    }

    @Test
    void provisionsAndRetriesAfterAccessDenied() {
        Path runtimeRoot = tempDir.resolve("Nmap/zenmap/bin/BurpPythonIDE");
        AtomicInteger probeCalls = new AtomicInteger();
        AtomicBoolean writable = new AtomicBoolean(false);
        AtomicBoolean prompted = new AtomicBoolean(false);
        AtomicBoolean provisioned = new AtomicBoolean(false);

        RuntimeProvisioningProbe probe = ignoredRoot -> {
            if (probeCalls.incrementAndGet() == 1 && !writable.get()) {
                throw new AccessDeniedException(runtimeRoot.toString());
            }
            Files.createDirectories(runtimeRoot);
        };

        RuntimeProvisioningOutcome outcome = new RuntimeProvisioningWorkflow(
            runtimeRoot,
            probe,
            (ignoredRoot, ignoredFailure) -> {
                prompted.set(true);
                return true;
            },
            ignoredRoot -> {
                provisioned.set(true);
                writable.set(true);
            }
        ).ensureReady();

        assertTrue(outcome.ready());
        assertTrue(prompted.get());
        assertTrue(provisioned.get());
        assertTrue(probeCalls.get() >= 2);
    }

    @Test
    void returnsNotReadyWhenUserDeclinesProvisioning() {
        Path runtimeRoot = tempDir.resolve("Nmap/zenmap/bin/BurpPythonIDE");
        AtomicBoolean provisioned = new AtomicBoolean(false);

        RuntimeProvisioningOutcome outcome = new RuntimeProvisioningWorkflow(
            runtimeRoot,
            ignoredRoot -> {
                throw new AccessDeniedException(runtimeRoot.toString());
            },
            (ignoredRoot, ignoredFailure) -> false,
            ignoredRoot -> provisioned.set(true)
        ).ensureReady();

        assertFalse(outcome.ready());
        assertTrue(outcome.message().contains("declined"));
        assertFalse(provisioned.get());
    }

    @Test
    void returnsNotReadyWhenProvisioningDoesNotRestoreWritability() {
        Path runtimeRoot = tempDir.resolve("Nmap/zenmap/bin/BurpPythonIDE");

        RuntimeProvisioningOutcome outcome = new RuntimeProvisioningWorkflow(
            runtimeRoot,
            ignoredRoot -> {
                throw new AccessDeniedException(runtimeRoot.toString());
            },
            (ignoredRoot, ignoredFailure) -> true,
            ignoredRoot -> { }
        ).ensureReady();

        assertFalse(outcome.ready());
        assertTrue(outcome.message().contains("still not writable"));
    }
}
