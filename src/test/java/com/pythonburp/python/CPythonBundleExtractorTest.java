package com.pythonburp.python;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CPythonBundleExtractorTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsRuntimeResourceTreeIntoVersionedCache() throws Exception {
        CPythonBundleExtractor extractor = new CPythonBundleExtractor(
            tempDir,
            CPythonBundleExtractorTest.class,
            "/cpython-test/windows-x64",
            "test-runtime"
        );

        Path root = extractor.extract();

        assertTrue(Files.isRegularFile(root.resolve("python.exe")));
        assertEquals("fake-python", Files.readString(root.resolve("python.exe")).stripTrailing());
        assertTrue(Files.isRegularFile(root.resolve("Lib/site-packages/example/__init__.py")));
        assertTrue(Files.isRegularFile(root.resolve(".burp-python-cpython-ready")));
    }

    @Test
    void reusesReadyCacheWithoutReextracting() throws Exception {
        CPythonBundleExtractor extractor = new CPythonBundleExtractor(
            tempDir,
            CPythonBundleExtractorTest.class,
            "/cpython-test/windows-x64",
            "test-runtime"
        );

        Path root = extractor.extract();
        Files.writeString(root.resolve("python.exe"), "mutated");

        Path reused = extractor.extract();

        assertEquals(root, reused);
        assertEquals("mutated", Files.readString(reused.resolve("python.exe")));
    }
}
