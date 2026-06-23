package com.pythonburp.python;

import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.storage.ExtensionDataPaths;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;

public final class CPythonRuntimeFactory implements Supplier<PythonRuntime> {
    public static final String RESOURCE_ROOT = "/cpython/windows-x64";
    public static final String RUNTIME_ID = "cpython-3.12.10-popular-pypdf-rpc1";

    private final NmapRuntimePaths runtimePaths;
    private final ExtensionDataPaths paths;

    public CPythonRuntimeFactory() {
        this(NmapRuntimePaths.fixed(), ExtensionDataPaths.windowsDefault());
    }

    public CPythonRuntimeFactory(ExtensionDataPaths paths) {
        this(NmapRuntimePaths.fixed(), paths);
    }

    CPythonRuntimeFactory(NmapRuntimePaths runtimePaths, ExtensionDataPaths paths) {
        this.runtimePaths = runtimePaths;
        this.paths = paths;
    }

    @Override
    public PythonRuntime get() {
        return get(new BurpBridge());
    }

    public PythonRuntime get(BurpBridge bridge) {
        try {
            Path runtimeRoot = prepareRuntimeRoot();
            return new CPythonWorkerRuntime(
                CPythonWorkerCommand.forExecutable(runtimeRoot.resolve("python.exe")),
                runtimeRoot.resolve("work"),
                bridge,
                paths.userPackages()
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare embedded CPython runtime", e);
        }
    }

    public Path prepareRuntimeRoot() throws IOException {
        return new CPythonBundleExtractor(
            runtimePaths.workerCacheRoot(),
            CPythonRuntimeFactory.class,
            RESOURCE_ROOT,
            RUNTIME_ID
        ).extract();
    }

    public Path pythonExecutable() {
        try {
            return prepareRuntimeRoot().resolve("python.exe");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare embedded CPython runtime", e);
        }
    }
}
