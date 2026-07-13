package com.pythonburp.python;

import com.pythonburp.concurrency.IdeExecutors;
import com.pythonburp.concurrency.RuntimeActivityCoordinator;

import java.util.Objects;
import java.util.concurrent.Future;
import java.util.function.Supplier;

public final class ScriptExecutor {
    private final IdeExecutors executors;
    private final Supplier<PythonRuntime> runtimeFactory;
    private final RuntimeActivityCoordinator coordinator;

    public ScriptExecutor(IdeExecutors executors, Supplier<PythonRuntime> runtimeFactory) {
        this(executors, runtimeFactory, new RuntimeActivityCoordinator());
    }

    public ScriptExecutor(IdeExecutors executors, Supplier<PythonRuntime> runtimeFactory,
                          RuntimeActivityCoordinator coordinator) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    public Future<ScriptRunResult> run(ScriptRunRequest request) {
        Objects.requireNonNull(request, "request");
        RuntimeActivityCoordinator.Lease lease = coordinator.beginScript();
        try {
            return executors.submitScript(() -> {
                try (lease; PythonRuntime runtime = runtimeFactory.get()) {
                    return runtime.execute(request);
                }
            });
        } catch (RuntimeException e) {
            lease.close();
            throw e;
        }
    }
}
