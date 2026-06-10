package com.pythonburp.cache;

import java.util.Locale;
import java.util.Objects;

public record CacheKey(
    String extensionVersion,
    String graalPyVersion,
    String os,
    String arch,
    String catalogHash
) {
    public CacheKey {
        Objects.requireNonNull(extensionVersion, "extensionVersion");
        Objects.requireNonNull(graalPyVersion, "graalPyVersion");
        Objects.requireNonNull(os, "os");
        Objects.requireNonNull(arch, "arch");
        Objects.requireNonNull(catalogHash, "catalogHash");
    }

    public String directoryName() {
        return sanitize(extensionVersion + "-" + graalPyVersion + "-" + os + "-" + arch + "-" + catalogHash);
    }

    private static String sanitize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    }
}
