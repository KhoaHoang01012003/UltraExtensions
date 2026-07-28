package com.pythonburp.packages;

import com.pythonburp.storage.ExtensionDataPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SharedPackageEnvironmentTest {
    @TempDir
    Path tempDir;

    @Test
    void failedBuildLeavesActiveEnvironmentUntouched() throws Exception {
        ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("data"));
        SharedPackageEnvironment environment = new SharedPackageEnvironment(paths);
        Files.createDirectories(paths.userPackages());
        Files.writeString(paths.userPackages().resolve("old.txt"), "old");

        assertThrows(IOException.class, () -> environment.replaceWith(staging -> {
            Files.writeString(staging.resolve("new.txt"), "new");
            throw new IOException("failed");
        }));

        assertEquals("old", Files.readString(paths.userPackages().resolve("old.txt")));
    }

    @Test
    void successfulBuildReplacesActiveEnvironment() throws Exception {
        ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("data"));
        SharedPackageEnvironment environment = new SharedPackageEnvironment(paths);

        environment.replaceWith(staging -> Files.writeString(staging.resolve("new.txt"), "new"));

        assertTrue(Files.exists(paths.userPackages().resolve("new.txt")));
    }
}
