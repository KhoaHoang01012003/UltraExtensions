package com.pythonburp.nativepack;

import com.pythonburp.cache.CacheKey;
import com.pythonburp.cache.CacheManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NativePackExtractor {
    private final CacheManager cacheManager;
    private final Class<?> resourceClass;
    private final NativePackManifest manifest;

    public NativePackExtractor(CacheManager cacheManager, Class<?> resourceClass, NativePackManifest manifest) {
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager");
        this.resourceClass = Objects.requireNonNull(resourceClass, "resourceClass");
        this.manifest = Objects.requireNonNull(manifest, "manifest");
    }

    public NativePackExtraction extract(String packId, String os, String arch) throws NativePackException {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(os, "os");
        Objects.requireNonNull(arch, "arch");

        List<NativePackResource> resources = manifest.resourcesFor(packId, os, arch);
        if (resources.isEmpty()) {
            throw new NativePackException("No native pack resources for " + packId + " on " + os + "/" + arch);
        }

        try {
            CacheKey key = new CacheKey("native-pack", packId, os, arch, manifest.fingerprint());
            Path cacheRoot = cacheManager.prepareCache(key);
            List<Path> files = new ArrayList<>();
            for (NativePackResource resource : resources) {
                byte[] content = readResource(resource.resourcePath());
                files.add(cacheManager.writeVerified(cacheRoot, resource.targetPath(), content, resource.sha256()));
            }
            return new NativePackExtraction(packId, os, arch, cacheRoot, files);
        } catch (IOException e) {
            throw new NativePackException("Failed to extract native pack " + packId + " for " + os + "/" + arch, e);
        }
    }

    private byte[] readResource(String resourcePath) throws IOException {
        String normalized = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
        try (InputStream stream = resourceClass.getResourceAsStream(normalized)) {
            if (stream == null) {
                throw new IOException("Missing native pack resource " + normalized);
            }
            return stream.readAllBytes();
        }
    }
}
