package com.pythonburp.nativepack;

import com.pythonburp.cache.CacheManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NativePackExtractorTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsMatchingResourcesIntoCacheWithHashVerification() throws Exception {
        NativePackManifest manifest = new NativePackManifest(List.of(
            new NativePackResource(
                "windows-x64-core",
                "windows",
                "amd64",
                "test-native-pack/hello.txt",
                "native/windows-x64-core/hello.txt",
                "f4857191504c9a665d80d2d2e867cc55d762d40cb2f5475b60ab1cfcc23091f0"
            ),
            new NativePackResource(
                "linux-x64-core",
                "linux",
                "amd64",
                "test-native-pack/hello.txt",
                "native/linux-x64-core/hello.txt",
                "f4857191504c9a665d80d2d2e867cc55d762d40cb2f5475b60ab1cfcc23091f0"
            )
        ));
        NativePackExtractor extractor = new NativePackExtractor(
            new CacheManager(tempDir),
            NativePackExtractorTest.class,
            manifest
        );

        NativePackExtraction extraction = extractor.extract("windows-x64-core", "windows", "amd64");

        assertEquals(1, extraction.files().size());
        Path extracted = extraction.cacheRoot().resolve("native/windows-x64-core/hello.txt");
        assertTrue(Files.isRegularFile(extracted));
        assertEquals("native-content", Files.readString(extracted).stripTrailing());
    }

    @Test
    void rejectsResourcesWithUnexpectedHash() {
        NativePackManifest manifest = new NativePackManifest(List.of(
            new NativePackResource(
                "windows-x64-core",
                "windows",
                "amd64",
                "test-native-pack/hello.txt",
                "native/windows-x64-core/hello.txt",
                "bad"
            )
        ));
        NativePackExtractor extractor = new NativePackExtractor(
            new CacheManager(tempDir),
            NativePackExtractorTest.class,
            manifest
        );

        assertThrows(NativePackException.class, () -> extractor.extract("windows-x64-core", "windows", "amd64"));
    }
}
