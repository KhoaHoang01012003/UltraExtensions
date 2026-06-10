package com.pythonburp.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
        String identity = rawIdentity();
        return sanitize(extensionVersion + "-" + graalPyVersion + "-" + os + "-" + arch + "-" + catalogHash)
            + "-" + shortHash(identity);
    }

    private static String sanitize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    }

    private String rawIdentity() {
        return String.join("\u001F", extensionVersion, graalPyVersion, os, arch, catalogHash);
    }

    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
