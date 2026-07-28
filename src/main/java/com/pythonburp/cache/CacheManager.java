package com.pythonburp.cache;

import com.pythonburp.storage.ExtensionDataPaths;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

        String actual = sha256(content);
        if (!actual.equalsIgnoreCase(expectedSha256)) {
            throw new IOException("SHA-256 mismatch for " + relativePath + ": expected " + expectedSha256 + " but got " + actual);
        }
        ensureSafeParent(confinedCache, target.getParent());
        verifySafeTarget(confinedCache, target);
        Path staging = Files.createTempFile(target.getParent(), ".cache-", ".tmp");
        try {
            Files.write(staging, content);
            try {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staging);
        }
        return target;
    }

    public static Path defaultWindowsRoot() {
        return ExtensionDataPaths.windowsDefault().runtimeRoot();
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

    private static void ensureSafeParent(Path confinedCache, Path targetParent) throws IOException {
        Path cacheReal = confinedCache.toRealPath();
        Path current = confinedCache;
        Path relativeParent = confinedCache.relativize(targetParent);
        for (Path segment : relativeParent) {
            current = current.resolve(segment).normalize();
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Refusing to use non-directory cache parent: " + current);
                }
                if (!current.toRealPath().startsWith(cacheReal)) {
                    throw new IOException("Refusing to use cache parent outside cache: " + current);
                }
            } else {
                Files.createDirectory(current);
                if (!current.toRealPath().startsWith(cacheReal)) {
                    throw new IOException("Created cache parent outside cache: " + current);
                }
            }
        }
    }

    private static void verifySafeTarget(Path confinedCache, Path target) throws IOException {
        Path cacheReal = confinedCache.toRealPath();
        if (Files.isSymbolicLink(target)) {
            throw new IOException("Refusing to write through symbolic link: " + target);
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !target.toRealPath().startsWith(cacheReal)) {
            throw new IOException("Refusing to overwrite target outside cache: " + target);
        }
        // Existing ancestors are resolved before creation, but this is still not a full TOCTOU
        // defense against concurrent filesystem mutation between validation and Files.write.
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
