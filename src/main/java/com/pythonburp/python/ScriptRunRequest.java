package com.pythonburp.python;

import java.time.Duration;
import java.util.Objects;

public record ScriptRunRequest(ScriptExecutionMode mode, String source, String commandTail, Duration timeout) {
    public ScriptRunRequest {
        mode = Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(source, "source");
        commandTail = commandTail == null ? "" : commandTail;
        Objects.requireNonNull(timeout, "timeout");
        if (mode == ScriptExecutionMode.CUSTOM_COMMAND && commandTail.isBlank()) {
            throw new IllegalArgumentException("commandTail must not be blank for custom command mode");
        }
    }

    public static ScriptRunRequest editorScript(String source, Duration timeout) {
        return new ScriptRunRequest(ScriptExecutionMode.EDITOR_SCRIPT, source, "", timeout);
    }

    public static ScriptRunRequest customCommand(String commandTail, Duration timeout) {
        return new ScriptRunRequest(ScriptExecutionMode.CUSTOM_COMMAND, "", commandTail, timeout);
    }
}
