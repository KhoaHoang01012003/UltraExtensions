package com.pythonburp.catalog;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PackageCatalogLoaderTest {
    @Test
    void loadsBundledCatalog() throws Exception {
        PackageCatalog catalog = PackageCatalogLoader.loadBundled();

        assertEquals(4, catalog.entries().size());

        Map<String, PackageCatalogEntry> entries = catalog.entries().stream()
            .collect(Collectors.toMap(PackageCatalogEntry::name, entry -> entry));

        assertTrue(entries.containsKey("burp.crypto"));
        assertEquals("java-backed", entries.get("burp.crypto").tier());
        assertEquals("0.1.0", entries.get("burp.encoder").version());
        assertEquals("locked-by-graalpy", entries.get("html5lib").version());
        assertEquals("tested-graalpy", entries.get("pyjwt").tier());
        assertTrue(entries.get("pyjwt").smokeTest().contains("import jwt"));
    }

    @Test
    void parsesEscapedSmokeTests() throws Exception {
        PackageCatalog catalog = PackageCatalogLoader.parse("""
            [
              {
                "name": "example",
                "version": "1.0.0",
                "tier": "pure-python",
                "nativeRequired": false,
                "smokeTest": "print(\\"brace } quote\\")"
              }
            ]
            """);

        assertEquals(1, catalog.entries().size());
        assertEquals("print(\"brace } quote\")", catalog.entries().get(0).smokeTest());
    }

    @Test
    void parsesOptionalNativePackId() throws Exception {
        PackageCatalog catalog = PackageCatalogLoader.parse("""
            [
              {
                "name": "native-example",
                "version": "1.0.0",
                "tier": "native-candidate",
                "nativeRequired": true,
                "nativePack": "windows-x64-core",
                "smokeTest": "import native_example"
              }
            ]
            """);

        PackageCatalogEntry entry = catalog.entries().get(0);
        assertTrue(entry.nativeRequired());
        assertEquals("windows-x64-core", entry.nativePackId().orElseThrow());
    }

    @Test
    void rejectsUnknownFields() {
        assertThrows(IOException.class, () -> PackageCatalogLoader.parse("""
            [
              {
                "name": "example",
                "version": "1.0.0",
                "tier": "pure-python",
                "nativeRequired": false,
                "smokeTest": "print(1)",
                "unexpected": "value"
              }
            ]
            """));
    }
}
