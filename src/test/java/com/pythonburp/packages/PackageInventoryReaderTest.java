package com.pythonburp.packages;

import com.pythonburp.catalog.PackageCatalog;
import com.pythonburp.catalog.PackageCatalogEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PackageInventoryReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void mergesUserDistributionWithBundledFallback() throws Exception {
        Path dist = tempDir.resolve("requests-9.9.9.dist-info");
        Files.createDirectories(dist);
        Files.writeString(dist.resolve("METADATA"), "Name: requests\nVersion: 9.9.9\n");
        Files.writeString(tempDir.resolve("native.pyd"), "x");
        PackageCatalog bundled = new PackageCatalog(List.of(
            new PackageCatalogEntry("requests", "bundled-cpython", "cpython-wheel", false, "import requests")
        ));

        List<PackageInventoryEntry> entries = new PackageInventoryReader(bundled).read(tempDir);

        assertEquals("9.9.9", entries.get(0).activeVersion());
        assertEquals("User cache", entries.get(0).source());
        assertEquals("bundled-cpython", entries.get(0).bundledFallback());
        assertTrue(entries.get(0).nativeFiles());
    }
}
