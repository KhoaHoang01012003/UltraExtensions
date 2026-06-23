package com.pythonburp.core;

import burp.api.montoya.MontoyaApi;
import com.pythonburp.concurrency.IdeExecutors;

import java.util.Objects;

public final class ExtensionContext implements AutoCloseable {
    private final MontoyaApi api;
    private final IdeExecutors executors;

    public ExtensionContext(MontoyaApi api, IdeExecutors executors) {
        this.api = Objects.requireNonNull(api, "api");
        this.executors = Objects.requireNonNull(executors, "executors");
    }

    public MontoyaApi api() {
        return api;
    }

    public IdeExecutors executors() {
        return executors;
    }

    @Override
    public void close() {
        executors.close();
    }
}
