package com.pythonburp.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class ExtensionDataPaths {
    private final Path root;

    public ExtensionDataPaths(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    public static ExtensionDataPaths windowsDefault() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = local == null || local.isBlank()
            ? Path.of(System.getProperty("user.home"), "AppData", "Local")
            : Path.of(local);
        return new ExtensionDataPaths(base.resolve("BurpPythonIDE"));
    }

    public Path root() { return root; }
    public Path runtimeRoot() { return root.resolve("runtime"); }
    public Path userPackages() { return root.resolve("packages/cpython-3.12-windows-x64"); }
    public Path packageStagingRoot() { return root.resolve("packages/staging"); }
    public Path packageRequests() { return root.resolve("packages/requests.properties"); }
    public Path packageSources() { return root.resolve("packages/sources"); }
    public Path pipCache() { return root.resolve("pip-cache"); }
    public Path temp() { return root.resolve("temp"); }
    public Path logs() { return root.resolve("logs"); }
    public Path settings() { return root.resolve("settings"); }

    public boolean isOwned(Path candidate) {
        return candidate != null && candidate.toAbsolutePath().normalize().startsWith(root);
    }

    public Path requireOwned(Path candidate) throws IOException {
        Path normalized = Objects.requireNonNull(candidate, "candidate").toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new IOException("Refusing path outside extension data root: " + candidate);
        }
        return normalized;
    }
}
