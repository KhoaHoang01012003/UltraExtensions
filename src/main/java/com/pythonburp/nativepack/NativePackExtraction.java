package com.pythonburp.nativepack;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record NativePackExtraction(
    String packId,
    String os,
    String arch,
    Path cacheRoot,
    List<Path> files
) {
    public NativePackExtraction {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(os, "os");
        Objects.requireNonNull(arch, "arch");
        Objects.requireNonNull(cacheRoot, "cacheRoot");
        Objects.requireNonNull(files, "files");
        files = List.copyOf(files);
    }
}
