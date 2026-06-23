package com.pythonburp.storage;

import com.pythonburp.packages.SharedPackageEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ExtensionDataCleaner {
    private final ExtensionDataPaths paths;

    public ExtensionDataCleaner(ExtensionDataPaths paths) {
        this.paths = paths;
    }

    public void clearUserPackages() throws IOException {
        clearDirectory(paths.userPackages());
    }

    public void clearPipCache() throws IOException {
        clearDirectory(paths.pipCache());
    }

    public Path resetAll() throws IOException {
        Path root = paths.root();
        Path parent = root.getParent();
        if (parent == null || !root.getFileName().toString().equals("BurpPythonIDE")) {
            throw new IOException("Unsafe extension data root: " + root);
        }
        if (!Files.exists(root)) {
            Files.createDirectories(root);
            return root;
        }
        Path tombstone = parent.resolve("BurpPythonIDE.delete-pending-" + Instant.now().toEpochMilli());
        Files.move(root, tombstone, StandardCopyOption.REPLACE_EXISTING);
        Files.createDirectories(root);
        tryDelete(tombstone);
        return tombstone;
    }

    public List<Path> cleanupPending() throws IOException {
        Path parent = paths.root().getParent();
        if (parent == null || !Files.exists(parent)) return List.of();
        List<Path> remaining = new ArrayList<>();
        try (var children = Files.list(parent)) {
            for (Path candidate : children.filter(path -> path.getFileName().toString()
                .startsWith("BurpPythonIDE.delete-pending-")).toList()) {
                tryDelete(candidate);
                if (Files.exists(candidate)) remaining.add(candidate);
            }
        }
        return List.copyOf(remaining);
    }

    private void clearDirectory(Path directory) throws IOException {
        paths.requireOwned(directory);
        if (Files.exists(directory)) {
            Path tombstone = paths.requireOwned(paths.packageStagingRoot().resolve(
                "delete-pending-" + directory.getFileName() + "-" + Instant.now().toEpochMilli()));
            Files.createDirectories(tombstone.getParent());
            Files.move(directory, tombstone, StandardCopyOption.REPLACE_EXISTING);
            tryDelete(tombstone);
        }
        Files.createDirectories(directory);
    }

    private static void tryDelete(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var entries = Files.walk(root)) {
            for (Path path : entries.sorted(Comparator.reverseOrder()).toList()) {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            }
        }
    }
}
