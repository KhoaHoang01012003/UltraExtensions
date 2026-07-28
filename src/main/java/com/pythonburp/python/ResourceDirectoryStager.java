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

public final class ResourceDirectoryStager {
    private static final String READY_MARKER = ".burp-python-resource-ready";

    private final Path stagingRoot;
    private final Class<?> resourceClass;
    private final String resourceRoot;
    private final String resourceDirectoryName;
    private final String stageId;

    public ResourceDirectoryStager(Path stagingRoot, Class<?> resourceClass, String resourceRoot,
                                   String resourceDirectoryName, String stageId) {
        this.stagingRoot = Objects.requireNonNull(stagingRoot, "stagingRoot").toAbsolutePath().normalize();
        this.resourceClass = Objects.requireNonNull(resourceClass, "resourceClass");
        this.resourceRoot = normalizeRoot(resourceRoot);
        this.resourceDirectoryName = requireText(resourceDirectoryName, "resourceDirectoryName");
        this.stageId = requireText(stageId, "stageId");
    }

    public Path stage() throws IOException {
        Path targetRoot = stagingRoot.resolve(stageId).normalize();
        if (!targetRoot.startsWith(stagingRoot)) {
            throw new IOException("Refusing to stage resources outside staging root: " + targetRoot);
        }
        Path marker = targetRoot.resolve(READY_MARKER);
        if (Files.exists(marker)) {
            return targetRoot;
        }

        Files.createDirectories(targetRoot);
        Path targetDirectory = targetRoot.resolve(resourceDirectoryName).normalize();
        if (!targetDirectory.startsWith(targetRoot)) {
            throw new IOException("Refusing to stage resource directory outside target root: " + targetDirectory);
        }

        URL root = resourceClass.getResource(resourceRoot);
        if (root == null) {
            throw new IOException("Missing resource root " + resourceRoot);
        }

        if ("file".equals(root.getProtocol())) {
            copyFileTree(root, targetDirectory);
        } else if ("jar".equals(root.getProtocol())) {
            copyJarTree(root, targetDirectory);
        } else {
            throw new IOException("Unsupported resource protocol: " + root.getProtocol());
        }

        Files.writeString(marker, "ready");
        return targetRoot;
    }

    private void copyFileTree(URL root, Path targetDirectory) throws IOException {
        try {
            Path source = Path.of(root.toURI());
            try (var paths = Files.walk(source)) {
                for (Path path : paths.toList()) {
                    Path relative = source.relativize(path);
                    Path destination = targetDirectory.resolve(relative.toString()).normalize();
                    if (!destination.startsWith(targetDirectory)) {
                        throw new IOException("Refusing to copy outside staged resource root: " + relative);
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
            throw new IOException("Invalid resource URI", e);
        }
    }

    private void copyJarTree(URL root, Path targetDirectory) throws IOException {
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
                Path destination = targetDirectory.resolve(relativeName).normalize();
                if (!destination.startsWith(targetDirectory)) {
                    throw new IOException("Refusing to copy outside staged resource root: " + relativeName);
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
