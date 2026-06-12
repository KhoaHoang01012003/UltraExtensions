package com.pythonburp.python;

import com.pythonburp.cache.CacheManager;
import com.pythonburp.bridge.BurpBridge;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;

public final class CPythonRuntimeFactory implements Supplier<PythonRuntime> {
    public static final String RESOURCE_ROOT = "/cpython/windows-x64";
    public static final String RUNTIME_ID = "cpython-3.12.10-popular-pypdf-rpc1";

    private final CPythonBundleExtractor extractor;

    public CPythonRuntimeFactory() {
        this(new CPythonBundleExtractor(
            CacheManager.defaultWindowsRoot().resolve("cpython-worker"),
            CPythonRuntimeFactory.class,
            RESOURCE_ROOT,
            RUNTIME_ID
        ));
    }

    CPythonRuntimeFactory(CPythonBundleExtractor extractor) {
        this.extractor = extractor;
    }

    @Override
    public PythonRuntime get() {
        return get(new BurpBridge());
    }

    public PythonRuntime get(BurpBridge bridge) {
        try {
            Path runtimeRoot = extractor.extract();
            return new CPythonWorkerRuntime(
                CPythonWorkerCommand.forExecutable(runtimeRoot.resolve("python.exe")),
                runtimeRoot.resolve("work"),
                bridge
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare embedded CPython runtime", e);
        }
    }
}
