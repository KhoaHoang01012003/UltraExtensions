package com.pythonburp.python;

public interface PythonRuntime extends AutoCloseable {
    ScriptRunResult execute(String source);

    @Override
    void close();
}
