package com.pythonburp.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class CacheManager {
    private final Path root;

    public CacheManager(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    public Path prepareCache(CacheKey key) throws IOException {
        Objects.requireNonNull(key, "key");

        Path cache = root.resolve(key.directoryName()).normalize();
        Files.createDirectories(cache);
        return cache;
    }

    public Path writeVerified(Path cache, String relativePath, byte[] content, String expectedSha256) throws IOException {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(expectedSha256, "expectedSha256");

        Path requested = Path.of(Objects.requireNonNull(relativePath, "relativePath"));
        if (requested.isAbsolute()) {
            throw new IOException("Refusing to write absolute path into cache: " + relativePath);
        }

        Path confinedCache = confinedCache(cache);
        Path target = confinedCache.resolve(requested).normalize();
        if (!target.startsWith(confinedCache)) {
            throw new IOException("Refusing to write outside cache: " + relativePath);
        }
        verifyParentRealPath(confinedCache, target);

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

    private Path confinedCache(Path cache) throws IOException {
        Path normalizedCache = Objects.requireNonNull(cache, "cache").toAbsolutePath().normalize();
        if (!normalizedCache.startsWith(root)) {
            throw new IOException("Refusing to use cache outside manager root: " + cache);
        }
        Path rootReal = root.toRealPath();
        Path cacheReal = normalizedCache.toRealPath();
        if (!cacheReal.startsWith(rootReal)) {
            throw new IOException("Refusing to use cache outside manager root: " + cache);
        }
        return normalizedCache;
    }

    private static void verifyParentRealPath(Path confinedCache, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path cacheReal = confinedCache.toRealPath();
        Path parentReal = target.getParent().toRealPath();
        if (!parentReal.startsWith(cacheReal)) {
            throw new IOException("Refusing to write through cache path outside cache: " + target);
        }
        if (Files.isSymbolicLink(target)) {
            throw new IOException("Refusing to write through symbolic link: " + target);
        }
        // This pre-write check resolves existing symlinks, but it is not a full TOCTOU defense
        // against concurrent filesystem mutation between validation and Files.write.
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !target.toRealPath().startsWith(cacheReal)) {
            throw new IOException("Refusing to overwrite target outside cache: " + target);
        }
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
