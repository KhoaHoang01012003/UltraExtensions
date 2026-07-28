package com.pythonburp.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExtensionDataCleanerTest {
    @TempDir
    Path tempDir;

    @Test
    void clearPackagesPreservesRuntimeSettingsAndExternalFile() throws Exception {
        ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("BurpPythonIDE"));
        ExtensionDataCleaner cleaner = new ExtensionDataCleaner(paths);
        Files.createDirectories(paths.userPackages());
        Files.createDirectories(paths.runtimeRoot());
        Files.createDirectories(paths.settings());
        Files.writeString(paths.userPackages().resolve("bad.py"), "bad");
        Path external = tempDir.resolve("saved.py");
        Files.writeString(external, "keep");

        cleaner.clearUserPackages();

        assertFalse(Files.exists(paths.userPackages().resolve("bad.py")));
        assertTrue(Files.isDirectory(paths.userPackages()));
        assertTrue(Files.isDirectory(paths.runtimeRoot()));
        assertTrue(Files.isDirectory(paths.settings()));
        assertTrue(Files.exists(external));
    }
}
