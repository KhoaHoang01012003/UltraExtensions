package com.pythonburp.nativepack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public record NativePackManifest(List<NativePackResource> resources) {
    public NativePackManifest {
        Objects.requireNonNull(resources, "resources");
        resources = List.copyOf(resources);
    }

    public List<NativePackResource> resourcesFor(String packId, String os, String arch) {
        return resources.stream()
            .filter(resource -> resource.matches(packId, os, arch))
            .toList();
    }

    public String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (NativePackResource resource : resources) {
                digest.update(resource.packId().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(resource.os().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(resource.arch().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(resource.resourcePath().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(resource.targetPath().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(resource.sha256().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
