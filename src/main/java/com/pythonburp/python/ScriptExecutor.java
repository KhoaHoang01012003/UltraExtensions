package com.pythonburp.python;

import com.pythonburp.concurrency.IdeExecutors;

import java.util.Objects;
import java.util.concurrent.Future;
import java.util.function.Supplier;

public final class ScriptExecutor {
    private final IdeExecutors executors;
    private final Supplier<PythonRuntime> runtimeFactory;

    public ScriptExecutor(IdeExecutors executors, Supplier<PythonRuntime> runtimeFactory) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
    }

    public Future<ScriptRunResult> run(ScriptRunRequest request) {
        Objects.requireNonNull(request, "request");
        return executors.submitScript(() -> {
            try (PythonRuntime runtime = runtimeFactory.get()) {
                return runtime.execute(request.source(), request.timeout());
            }
        });
    }
}
