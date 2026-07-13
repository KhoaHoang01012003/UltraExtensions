package com.pythonburp.python;

public enum ScriptExecutionMode {
    EDITOR_SCRIPT("Editor Script"),
    CUSTOM_COMMAND("Custom Command");

    private final String displayName;

    ScriptExecutionMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
