package com.pythonburp.packages;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EmbeddedPipRunnerTest {
    @TempDir Path tempDir;

    @Test
    void prependsCompatibilityRootWithoutDiscardingSystemPath() throws Exception {
        Path compatibilityRoot = Files.createDirectories(tempDir.resolve("compat-runtime"));
        String originalPath = System.getenv("PATH");
        EmbeddedPipRunner runner = new EmbeddedPipRunner(() -> Map.of("PATH", compatibilityRoot.toString()));

        PipRunResult result = runner.run(List.of(
            "powershell.exe", "-NoProfile", "-Command", "$env:PATH"
        ), ignored -> { });

        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(result.stdout().strip().startsWith(compatibilityRoot.toString() + File.pathSeparator));
        assertTrue(result.stdout().contains(originalPath));
    }
}
