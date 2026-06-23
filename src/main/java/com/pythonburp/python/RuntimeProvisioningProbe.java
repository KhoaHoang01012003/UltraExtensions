package com.pythonburp.python;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@FunctionalInterface
interface RuntimeProvisioningProbe {
    void ensureWritable(Path runtimeRoot) throws IOException;

    static RuntimeProvisioningProbe fileSystem() {
        return runtimeRoot -> {
            Files.createDirectories(runtimeRoot);
            Path probeFile = Files.createTempFile(runtimeRoot, "burp-python-probe-", ".tmp");
            Files.deleteIfExists(probeFile);
        };
    }
}
