package com.pythonburp.python;

import java.time.Duration;
import java.util.Objects;

public record ScriptRunRequest(String source, Duration timeout) {
    public ScriptRunRequest {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(timeout, "timeout");
    }
}
