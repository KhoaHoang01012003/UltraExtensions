package com.pythonburp.python;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
interface RuntimeProvisioner {
    void provision(Path runtimeRoot) throws IOException, InterruptedException;
}
