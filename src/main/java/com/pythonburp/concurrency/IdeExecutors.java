package com.pythonburp.concurrency;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class IdeExecutors implements AutoCloseable {
    private final ExecutorService scriptExecutor;
    private final ExecutorService packageExecutor;
    private final ExecutorService utilityExecutor;

    public IdeExecutors(int maxScriptThreads) {
        int scriptThreads = Math.max(1, maxScriptThreads);
        this.scriptExecutor = Executors.newFixedThreadPool(scriptThreads, namedFactory("burp-python-script"));
        this.packageExecutor = Executors.newSingleThreadExecutor(namedFactory("burp-python-package"));
        this.utilityExecutor = Executors.newSingleThreadExecutor(namedFactory("burp-python-utility"));
    }

    public <T> Future<T> submitScript(Callable<T> task) {
        return scriptExecutor.submit(Objects.requireNonNull(task, "task"));
    }

    public Future<?> submitScript(Runnable task) {
        return scriptExecutor.submit(Objects.requireNonNull(task, "task"));
    }

    public <T> Future<T> submitPackageTask(Callable<T> task) {
        return packageExecutor.submit(Objects.requireNonNull(task, "task"));
    }

    public Future<?> submitPackageTask(Runnable task) {
        return packageExecutor.submit(Objects.requireNonNull(task, "task"));
    }

    public <T> Future<T> submitUtilityTask(Callable<T> task) {
        return utilityExecutor.submit(Objects.requireNonNull(task, "task"));
    }

    public Future<?> submitUtilityTask(Runnable task) {
        return utilityExecutor.submit(Objects.requireNonNull(task, "task"));
    }

    @Override
    public void close() {
        scriptExecutor.shutdownNow();
        packageExecutor.shutdownNow();
        utilityExecutor.shutdownNow();
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger count = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
