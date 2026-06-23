package com.pythonburp.nativepack;

import java.util.Objects;

public record NativePackResource(
    String packId,
    String os,
    String arch,
    String resourcePath,
    String targetPath,
    String sha256
) {
    public NativePackResource {
        packId = requireText(packId, "packId");
        os = normalize(requireText(os, "os"));
        arch = normalize(requireText(arch, "arch"));
        resourcePath = requireText(resourcePath, "resourcePath");
        targetPath = requireText(targetPath, "targetPath");
        sha256 = requireText(sha256, "sha256").toLowerCase();
    }

    public boolean matches(String requestedPackId, String requestedOs, String requestedArch) {
        return packId.equals(requestedPackId)
            && os.equals(normalize(requestedOs))
            && arch.equals(normalize(requestedArch));
    }

    private static String normalize(String value) {
        return Objects.requireNonNull(value, "value").trim().toLowerCase();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
