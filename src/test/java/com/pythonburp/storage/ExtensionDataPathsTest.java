package com.pythonburp.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExtensionDataPathsTest {
    @TempDir
    Path tempDir;

    @Test
    void exposesOwnedPathsBelowOneRoot() {
        ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("BurpPythonIDE"));

        assertEquals(paths.root().resolve("runtime"), paths.runtimeRoot());
        assertEquals(paths.root().resolve("packages/cpython-3.12-windows-x64"), paths.userPackages());
        assertEquals(paths.root().resolve("pip-cache"), paths.pipCache());
        assertTrue(paths.isOwned(paths.userPackages()));
    }

    @Test
    void rejectsPathsOutsideOwnedRoot() {
        ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("BurpPythonIDE"));

        assertThrows(IOException.class, () -> paths.requireOwned(tempDir.resolve("outside")));
    }
}
