package com.pythonburp.concurrency;

import java.util.concurrent.atomic.AtomicBoolean;

public final class RuntimeActivityCoordinator {
    private int activeScripts;
    private boolean packageMutation;

    public synchronized Lease beginScript() {
        if (packageMutation) {
            throw new IllegalStateException("Package operation is active");
        }
        activeScripts++;
        return new Lease(this::endScript);
    }

    public synchronized Lease beginPackageMutation() {
        if (packageMutation || activeScripts > 0) {
            throw new IllegalStateException("Python runtime is busy");
        }
        packageMutation = true;
        return new Lease(this::endPackageMutation);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(activeScripts, packageMutation);
    }

    private synchronized void endScript() {
        activeScripts = Math.max(0, activeScripts - 1);
    }

    private synchronized void endPackageMutation() {
        packageMutation = false;
    }

    public record Snapshot(int activeScripts, boolean packageMutation) {
        public boolean busy() { return activeScripts > 0 || packageMutation; }
    }

    public static final class Lease implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final Runnable release;

        private Lease(Runnable release) {
            this.release = release;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release.run();
            }
        }
    }
}
