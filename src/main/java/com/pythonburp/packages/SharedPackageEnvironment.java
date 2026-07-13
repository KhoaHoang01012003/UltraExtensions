package com.pythonburp.packages;

import com.pythonburp.storage.ExtensionDataPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;

public final class SharedPackageEnvironment {
    private final ExtensionDataPaths paths;
    private final Path activePackages;

    public SharedPackageEnvironment(ExtensionDataPaths paths) {
        this(paths, paths.userPackages());
    }

    public SharedPackageEnvironment(ExtensionDataPaths paths, Path activePackages) {
        this.paths = paths;
        this.activePackages = paths.requireOwnedUnchecked(activePackages);
    }

    public void replaceWith(Builder builder) throws IOException {
        Files.createDirectories(paths.packageStagingRoot());
        Path staging = paths.requireOwned(paths.packageStagingRoot().resolve("build-" + UUID.randomUUID()));
        Files.createDirectories(staging);
        try {
            builder.build(staging);
        } catch (IOException | RuntimeException e) {
            deleteTree(staging);
            throw e;
        }

        Path active = activePackages;
        Path old = paths.requireOwned(paths.packageStagingRoot().resolve("old-" + UUID.randomUUID()));
        Files.createDirectories(active.getParent());
        boolean movedOld = false;
        try {
            if (Files.exists(active)) {
                Files.move(active, old, StandardCopyOption.REPLACE_EXISTING);
                movedOld = true;
            }
            Files.move(staging, active, StandardCopyOption.REPLACE_EXISTING);
            if (movedOld) deleteTree(old);
        } catch (IOException e) {
            if (!Files.exists(active) && movedOld && Files.exists(old)) {
                Files.move(old, active, StandardCopyOption.REPLACE_EXISTING);
            }
            deleteTree(staging);
            throw e;
        }
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @FunctionalInterface
    public interface Builder {
        void build(Path staging) throws IOException;
    }
}
