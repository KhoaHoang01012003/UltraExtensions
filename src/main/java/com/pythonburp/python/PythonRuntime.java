package com.pythonburp.python;

import java.time.Duration;

public interface PythonRuntime extends AutoCloseable {
    ScriptRunResult execute(ScriptRunRequest request);

    default ScriptRunResult execute(String source, Duration timeout) {
        return execute(ScriptRunRequest.editorScript(source, timeout));
    }

    @Override
    void close();
}
