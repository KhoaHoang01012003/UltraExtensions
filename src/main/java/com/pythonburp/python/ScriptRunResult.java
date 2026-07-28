package com.pythonburp.python;

public record ScriptRunResult(
    ScriptStatus status,
    String stdout,
    String stderr,
    String errorMessage
) {
    public static ScriptRunResult succeeded(String stdout, String stderr) {
        return new ScriptRunResult(ScriptStatus.SUCCEEDED, stdout, stderr, "");
    }

    public static ScriptRunResult failed(String stdout, String stderr, String errorMessage) {
        return new ScriptRunResult(ScriptStatus.FAILED, stdout, stderr, errorMessage);
    }
}
