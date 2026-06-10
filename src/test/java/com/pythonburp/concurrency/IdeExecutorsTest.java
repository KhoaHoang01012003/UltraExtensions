package com.pythonburp.concurrency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void scriptExecutorUsesNamedDaemonThreads() throws Exception {
        try (IdeExecutors executors = new IdeExecutors(1)) {
            Future<Thread> future = executors.submitScript(Thread::currentThread);

            Thread workerThread = future.get(5, TimeUnit.SECONDS);

            assertTrue(workerThread.getName().startsWith("burp-python-script-"));
            assertTrue(workerThread.isDaemon());
        }
    }

    @Test
    void scriptExecutorClampsThreadCountToAtLeastOne() throws Exception {
        try (IdeExecutors executors = new IdeExecutors(0)) {
            Future<String> future = executors.submitScript(() -> Thread.currentThread().getName());

            assertTrue(future.get(5, TimeUnit.SECONDS).startsWith("burp-python-script-"));
        }
    }

    @Test
    void packageExecutorRunsWork() throws Exception {
        try (IdeExecutors executors = new IdeExecutors(1)) {
            Future<Integer> future = executors.submitPackageTask(() -> 42);

            assertEquals(42, future.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void packageExecutorSerializesWorkOnOneThread() throws Exception {
        try (IdeExecutors executors = new IdeExecutors(2)) {
            Future<String> first = executors.submitPackageTask(() -> Thread.currentThread().getName());
            Future<String> second = executors.submitPackageTask(() -> Thread.currentThread().getName());

            assertEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void closeRejectsNewTasks() {
        IdeExecutors executors = new IdeExecutors(1);

        executors.close();

        assertThrows(RejectedExecutionException.class, () -> executors.submitScript(() -> 1));
        assertThrows(RejectedExecutionException.class, () -> executors.submitPackageTask(() -> 1));
    }
}
