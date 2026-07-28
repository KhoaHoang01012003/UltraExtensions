package com.pythonburp.python;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class CPythonBundleExtractor {
    private static final String READY_MARKER = ".burp-python-cpython-ready";

    private final Path cacheRoot;
    private final Class<?> resourceClass;
    private final String resourceRoot;
    private final String runtimeId;

    public CPythonBundleExtractor(Path cacheRoot, Class<?> resourceClass, String resourceRoot, String runtimeId) {
        this.cacheRoot = Objects.requireNonNull(cacheRoot, "cacheRoot").toAbsolutePath().normalize();
        this.resourceClass = Objects.requireNonNull(resourceClass, "resourceClass");
        this.resourceRoot = normalizeRoot(resourceRoot);
        this.runtimeId = requireText(runtimeId, "runtimeId");
    }

    public Path extract() throws IOException {
        Path target = cacheRoot.resolve(runtimeId).normalize();
        if (!target.startsWith(cacheRoot)) {
            throw new IOException("Refusing to extract CPython bundle outside cache root: " + target);
        }

        Path marker = target.resolve(READY_MARKER);
        if (Files.exists(marker)) {
            return target;
        }

        Files.createDirectories(target);
        URL root = resourceClass.getResource(resourceRoot);
        if (root == null) {
            throw new IOException("Missing CPython bundle resource root " + resourceRoot);
        }

        if ("file".equals(root.getProtocol())) {
            copyFileTree(root, target);
        } else if ("jar".equals(root.getProtocol())) {
            copyJarTree(root, target);
        } else {
            throw new IOException("Unsupported CPython bundle resource protocol: " + root.getProtocol());
        }
        Files.writeString(marker, "ready");
        return target;
    }

    private void copyFileTree(URL root, Path target) throws IOException {
        try {
            Path source = Path.of(root.toURI());
            try (var paths = Files.walk(source)) {
                for (Path path : paths.toList()) {
                    Path relative = source.relativize(path);
                    Path destination = target.resolve(relative.toString()).normalize();
                    if (!destination.startsWith(target)) {
                        throw new IOException("Refusing to copy outside CPython cache: " + relative);
                    }
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (URISyntaxException e) {
            throw new IOException("Invalid CPython bundle resource URI", e);
        }
    }

    private void copyJarTree(URL root, Path target) throws IOException {
        JarURLConnection connection = (JarURLConnection) root.openConnection();
        String prefix = trimLeadingSlash(resourceRoot) + "/";
        try (JarFile jar = connection.getJarFile()) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(prefix) || name.equals(prefix)) {
                    continue;
                }
                String relativeName = name.substring(prefix.length());
                Path destination = target.resolve(relativeName).normalize();
                if (!destination.startsWith(target)) {
                    throw new IOException("Refusing to copy outside CPython cache: " + relativeName);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    try (var input = jar.getInputStream(entry)) {
                        Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    private static String normalizeRoot(String value) {
        String text = requireText(value, "resourceRoot").replace('\\', '/');
        return text.startsWith("/") ? text : "/" + text;
    }

    private static String trimLeadingSlash(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
