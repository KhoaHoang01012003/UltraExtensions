package com.pythonburp.python;

import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.concurrency.IdeExecutors;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Future;

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
}
