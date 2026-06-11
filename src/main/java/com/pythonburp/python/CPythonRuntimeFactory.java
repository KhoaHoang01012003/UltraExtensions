package com.pythonburp.python;

import com.pythonburp.cache.CacheManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;

public final class CPythonRuntimeFactory implements Supplier<PythonRuntime> {
    public static final String RESOURCE_ROOT = "/cpython/windows-x64";
    public static final String RUNTIME_ID = "cpython-3.12.10-popular";

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
        try {
            Path runtimeRoot = extractor.extract();
            return new CPythonWorkerRuntime(
                CPythonWorkerCommand.forExecutable(runtimeRoot.resolve("python.exe")),
                runtimeRoot.resolve("work")
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare embedded CPython runtime", e);
        }
    }
}
