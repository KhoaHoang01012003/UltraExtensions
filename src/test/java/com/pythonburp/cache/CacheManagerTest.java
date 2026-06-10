package com.pythonburp.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CacheManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void cachePathIncludesVersionAndHash() throws Exception {
        CacheKey key = new CacheKey("0.1.0", "25.0.3", "windows", "amd64", "abc123");
        CacheManager manager = new CacheManager(tempDir);

        Path path = manager.prepareCache(key);

        assertTrue(Files.isDirectory(path));
        assertTrue(path.getFileName().toString().contains("0.1.0"));
        assertTrue(path.getFileName().toString().contains("abc123"));
    }

    @Test
    void writeVerifiedWritesFileAndVerifiesSha256() throws Exception {
        CacheKey key = new CacheKey("0.1.0", "25.0.3", "windows", "amd64", "hash");
        CacheManager manager = new CacheManager(tempDir);
        Path cache = manager.prepareCache(key);
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        String sha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

        Path file = manager.writeVerified(cache, "native/test.txt", content, sha256);

        assertEquals("hello", Files.readString(file));
    }

    @Test
    void writeVerifiedRejectsSha256Mismatch() throws Exception {
        CacheKey key = new CacheKey("0.1.0", "25.0.3", "windows", "amd64", "hash");
        CacheManager manager = new CacheManager(tempDir);
        Path cache = manager.prepareCache(key);

        assertThrows(IOException.class, () ->
            manager.writeVerified(cache, "native/test.txt", "hello".getBytes(StandardCharsets.UTF_8), "bad"));
    }

    @Test
    void writeVerifiedRejectsParentDirectoryEscape() throws Exception {
        CacheKey key = new CacheKey("0.1.0", "25.0.3", "windows", "amd64", "hash");
        CacheManager manager = new CacheManager(tempDir);
        Path cache = manager.prepareCache(key);
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        String sha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

        assertThrows(IOException.class, () ->
            manager.writeVerified(cache, "../escape.txt", content, sha256));
    }

    @Test
    void writeVerifiedRejectsAbsolutePath() throws Exception {
        CacheKey key = new CacheKey("0.1.0", "25.0.3", "windows", "amd64", "hash");
        CacheManager manager = new CacheManager(tempDir);
        Path cache = manager.prepareCache(key);
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        String sha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
        Path absolutePath = tempDir.resolve("absolute.txt").toAbsolutePath();

        assertThrows(IOException.class, () ->
            manager.writeVerified(cache, absolutePath.toString(), content, sha256));
    }

    @Test
    void writeVerifiedRejectsCacheOutsideManagerRoot() throws Exception {
        CacheManager manager = new CacheManager(tempDir.resolve("root"));
        Path outsideCache = tempDir.resolve("outside");
        Files.createDirectories(outsideCache);
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        String sha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

        assertThrows(IOException.class, () ->
            manager.writeVerified(outsideCache, "native/test.txt", content, sha256));
    }

    @Test
    void directoryNameKeepsDistinctRawKeysFromCollapsing() {
        CacheKey punctuation = new CacheKey("0.1.0", "25.0.3", "windows", "amd64", "a/b");
        CacheKey space = new CacheKey("0.1.0", "25.0.3", "windows", "amd64", "a b");

        assertNotEquals(punctuation.directoryName(), space.directoryName());
    }
}
