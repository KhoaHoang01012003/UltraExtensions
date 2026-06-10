package com.pythonburp.concurrency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class IdeExecutorsTest {
    @Test
    void scriptExecutorRunsWorkOffCallingThread() throws Exception {
        try (IdeExecutors executors = new IdeExecutors(2)) {
            String callingThread = Thread.currentThread().getName();
            Future<String> future = executors.submitScript(() -> Thread.currentThread().getName());

            String workerThread = future.get(5, TimeUnit.SECONDS);

            assertFalse(workerThread.equals(callingThread));
            assertFalse(workerThread.isBlank());
        }
    }

    @Test
    void packageExecutorRunsWork() throws Exception {
        try (IdeExecutors executors = new IdeExecutors(1)) {
            Future<Integer> future = executors.submitPackageTask(() -> 42);

            assertEquals(42, future.get(5, TimeUnit.SECONDS));
        }
    }
}
