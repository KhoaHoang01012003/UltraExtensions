package com.pythonburp.python;

import com.pythonburp.cache.CacheManager;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

final class GraalPyResourceCache {
    static final GraalPyResourceCache DEFAULT = new GraalPyResourceCache(
        CacheManager.defaultWindowsRoot().resolve("graalpy-vfs").resolve("25.0.3"),
        GraalPyResources::extractVirtualFileSystemResources
    );

    private final Path extractionRoot;
    private final Extractor extractor;

    GraalPyResourceCache(Path extractionRoot, Extractor extractor) {
        this.extractionRoot = Objects.requireNonNull(extractionRoot, "extractionRoot").toAbsolutePath().normalize();
        this.extractor = Objects.requireNonNull(extractor, "extractor");
    }

    synchronized Path extractionRoot(VirtualFileSystem fileSystem) throws IOException {
        Path marker = extractionRoot.resolve(".burp-python-vfs-ready");
        if (Files.exists(marker)) {
            return extractionRoot;
        }

        Files.createDirectories(extractionRoot);
        extractor.extract(fileSystem, extractionRoot);
        Files.writeString(marker, "ready");
        return extractionRoot;
    }

    @FunctionalInterface
    interface Extractor {
        void extract(VirtualFileSystem fileSystem, Path target) throws IOException;
    }
}
