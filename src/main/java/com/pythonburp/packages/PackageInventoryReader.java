package com.pythonburp.packages;

import com.pythonburp.catalog.PackageCatalog;
import com.pythonburp.catalog.PackageCatalogEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PackageInventoryReader {
    private final PackageCatalog bundled;

    public PackageInventoryReader(PackageCatalog bundled) {
        this.bundled = bundled;
    }

    public List<PackageInventoryEntry> read(Path userPackages) throws IOException {
        Map<String, PackageCatalogEntry> bundledByName = new LinkedHashMap<>();
        for (PackageCatalogEntry entry : bundled.entries()) bundledByName.put(normalize(entry.name()), entry);
        Map<String, PackageInventoryEntry> result = new LinkedHashMap<>();
        boolean nativeFiles = containsNativeFiles(userPackages);
        if (Files.isDirectory(userPackages)) {
            try (var children = Files.list(userPackages)) {
                for (Path distInfo : children.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().endsWith(".dist-info")).toList()) {
                    Metadata metadata = metadata(distInfo.resolve("METADATA"));
                    if (metadata.name().isBlank()) continue;
                    PackageCatalogEntry fallback = bundledByName.get(normalize(metadata.name()));
                    result.put(normalize(metadata.name()), new PackageInventoryEntry(
                        metadata.name(), metadata.version(), "User cache",
                        fallback == null ? "" : fallback.version(), nativeFiles
                    ));
                }
            }
        }
        for (PackageCatalogEntry entry : bundled.entries()) {
            result.putIfAbsent(normalize(entry.name()), new PackageInventoryEntry(
                entry.name(), entry.version(), "Bundled", "", entry.nativeRequired()
            ));
        }
        return result.values().stream().sorted(Comparator.comparing(PackageInventoryEntry::name,
            String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static Metadata metadata(Path file) throws IOException {
        String name = "";
        String version = "";
        if (Files.exists(file)) {
            for (String line : Files.readAllLines(file)) {
                if (line.startsWith("Name:")) name = line.substring(5).trim();
                if (line.startsWith("Version:")) version = line.substring(8).trim();
            }
        }
        return new Metadata(name, version);
    }

    private static boolean containsNativeFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) return false;
        try (var files = Files.walk(root)) {
            return files.anyMatch(path -> {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.endsWith(".pyd") || name.endsWith(".dll");
            });
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private record Metadata(String name, String version) {}
}
