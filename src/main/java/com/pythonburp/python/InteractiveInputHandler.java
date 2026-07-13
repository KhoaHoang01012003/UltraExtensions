package com.pythonburp.python;

import java.io.IOException;

@FunctionalInterface
public interface InteractiveInputHandler {
    String requestInput(String prompt) throws IOException, InterruptedException;

    static InteractiveInputHandler disabled() {
        return prompt -> {
            throw new IOException("Script requested interactive input, but Interactive mode is disabled.");
        };
    }
}
