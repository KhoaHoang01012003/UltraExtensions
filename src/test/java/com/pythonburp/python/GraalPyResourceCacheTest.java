package com.pythonburp.python;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GraalPyResourceCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsOnceAndReusesCacheDirectory() throws Exception {
        AtomicInteger extractions = new AtomicInteger();
        GraalPyResourceCache cache = new GraalPyResourceCache(tempDir.resolve("runtime"), (fileSystem, target) -> {
            extractions.incrementAndGet();
            Files.writeString(target.resolve("marker.txt"), "ready");
        });

        Path first = cache.extractionRoot(null);
        Path second = cache.extractionRoot(null);

        assertSame(first, second);
        assertEquals(1, extractions.get());
        assertTrue(Files.exists(first.resolve("marker.txt")));
    }
}
