package com.pythonburp.python;

import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.concurrency.IdeExecutors;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ScriptExecutorTest {
    @Test
    void runReturnsImmediatelyWithFuture() throws Exception {
        try (IdeExecutors executors = new IdeExecutors(1)) {
            ScriptExecutor scriptExecutor = new ScriptExecutor(executors, () -> new GraalPyPythonRuntime(new BurpBridge()));
            ScriptRunRequest request = new ScriptRunRequest("print('ok')", Duration.ofSeconds(10));

            Future<ScriptRunResult> future = scriptExecutor.run(request);

            assertFalse(future.isDone() && Thread.currentThread().getName().startsWith("AWT-EventQueue"));
            ScriptRunResult result = future.get();
            assertEquals(ScriptStatus.SUCCEEDED, result.status(), result.errorMessage() + "\nSTDERR:\n" + result.stderr());
        }
    }

    @Test
    void passesRequestTimeoutToRuntime() throws Exception {
        try (IdeExecutors executors = new IdeExecutors(1)) {
            AtomicReference<Duration> observedTimeout = new AtomicReference<>();
            ScriptExecutor scriptExecutor = new ScriptExecutor(executors, () -> new PythonRuntime() {
                @Override
                public ScriptRunResult execute(String source, Duration timeout) {
                    observedTimeout.set(timeout);
                    return ScriptRunResult.succeeded("", "");
                }

                @Override
                public void close() {
                }
            });
            Duration timeout = Duration.ofSeconds(3);

            ScriptRunResult result = scriptExecutor.run(new ScriptRunRequest("print('ok')", timeout)).get();

            assertEquals(ScriptStatus.SUCCEEDED, result.status());
            assertEquals(timeout, observedTimeout.get());
        }
    }
}
