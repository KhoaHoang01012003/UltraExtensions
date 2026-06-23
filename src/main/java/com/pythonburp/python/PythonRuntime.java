package com.pythonburp.python;

import java.time.Duration;

public interface PythonRuntime extends AutoCloseable {
    ScriptRunResult execute(String source, Duration timeout);

    @Override
    void close();
}
