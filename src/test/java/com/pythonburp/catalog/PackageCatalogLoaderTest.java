package com.pythonburp.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PackageCatalogLoaderTest {
    @Test
    void loadsBundledCatalog() throws Exception {
        PackageCatalog catalog = PackageCatalogLoader.loadBundled();

        assertTrue(catalog.find("beautifulsoup4").isPresent());
        assertEquals("java-backed", catalog.find("burp.crypto").orElseThrow().tier());
    }
}
