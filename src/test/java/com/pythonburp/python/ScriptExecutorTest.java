package com.pythonburp.python;

import com.pythonburp.concurrency.IdeExecutors;
import com.pythonburp.concurrency.RuntimeActivityCoordinator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ScriptExecutorTest {
    @Test
    void runReturnsImmediatelyWithFuture() throws Exception {
        try (IdeExecutors executors = new IdeExecutors(1)) {
            ScriptExecutor scriptExecutor = new ScriptExecutor(executors, () -> new PythonRuntime() {
                @Override
                public ScriptRunResult execute(ScriptRunRequest request) {
                    return ScriptRunResult.succeeded("ok", "");
                }

                @Override
                public void close() {
                }
            });
            ScriptRunRequest request = ScriptRunRequest.editorScript("print('ok')", Duration.ofSeconds(10));

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
                public ScriptRunResult execute(ScriptRunRequest request) {
                    observedTimeout.set(request.timeout());
                    return ScriptRunResult.succeeded("", "");
                }

                @Override
                public void close() {
                }
            });
            Duration timeout = Duration.ofSeconds(3);

            ScriptRunResult result = scriptExecutor.run(ScriptRunRequest.editorScript("print('ok')", timeout)).get();

            assertEquals(ScriptStatus.SUCCEEDED, result.status());
            assertEquals(timeout, observedTimeout.get());
        }
    }

    @Test
    void scriptLeaseIsActiveUntilWorkerCompletes() throws Exception {
        try (IdeExecutors executors = new IdeExecutors(1)) {
            RuntimeActivityCoordinator coordinator = new RuntimeActivityCoordinator();
            CountDownLatch release = new CountDownLatch(1);
            ScriptExecutor scriptExecutor = new ScriptExecutor(executors, () -> new PythonRuntime() {
                @Override public ScriptRunResult execute(ScriptRunRequest request) {
                    try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    return ScriptRunResult.succeeded("", "");
                }
                @Override public void close() { }
            }, coordinator);

            Future<ScriptRunResult> run = scriptExecutor.run(ScriptRunRequest.editorScript("pass", Duration.ofSeconds(5)));

            assertEquals(1, coordinator.snapshot().activeScripts());
            release.countDown();
            run.get();
            assertEquals(0, coordinator.snapshot().activeScripts());
        }
    }
}
