package com.pythonburp.catalog;

public record PackageCatalogEntry(
    String name,
    String version,
    String tier,
    boolean nativeRequired,
    String smokeTest
) {
}
