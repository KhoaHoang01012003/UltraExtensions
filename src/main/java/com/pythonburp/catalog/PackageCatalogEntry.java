package com.pythonburp.catalog;

import java.util.Optional;

public record PackageCatalogEntry(
    String name,
    String version,
    String tier,
    boolean nativeRequired,
    Optional<String> nativePackId,
    String smokeTest
) {
    public PackageCatalogEntry(String name, String version, String tier, boolean nativeRequired, String smokeTest) {
        this(name, version, tier, nativeRequired, Optional.empty(), smokeTest);
    }

    public PackageCatalogEntry {
        nativePackId = nativePackId == null ? Optional.empty() : nativePackId;
    }
}
