package com.pythonburp.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class CacheManager {
    private final Path root;

    public CacheManager(Path root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    public Path prepareCache(CacheKey key) throws IOException {
        Path cache = root.resolve(key.directoryName()).normalize();
        Files.createDirectories(cache);
        return cache;
    }

    public Path writeVerified(Path cache, String relativePath, byte[] content, String expectedSha256) throws IOException {
        Path target = cache.resolve(relativePath).normalize();
        if (!target.startsWith(cache.normalize())) {
            throw new IOException("Refusing to write outside cache: " + relativePath);
        }
        String actual = sha256(content);
        if (!actual.equalsIgnoreCase(expectedSha256)) {
            throw new IOException("SHA-256 mismatch for " + relativePath + ": expected " + expectedSha256 + " but got " + actual);
        }
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        return target;
    }

    public static Path defaultWindowsRoot() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = localAppData == null || localAppData.isBlank()
            ? Path.of(System.getProperty("user.home"), "AppData", "Local")
            : Path.of(localAppData);
        return base.resolve("BurpPythonIDE").resolve("cache");
    }

    private static String sha256(byte[] content) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }
}
