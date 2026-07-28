package com.pythonburp.packages;

public record PipRunResult(int exitCode, boolean cancelled, String stdout, String stderr) {
    public boolean succeeded() { return !cancelled && exitCode == 0; }
}
