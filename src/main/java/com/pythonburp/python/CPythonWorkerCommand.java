package com.pythonburp.python;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record CPythonWorkerCommand(List<String> commandPrefix) {
    public CPythonWorkerCommand {
        Objects.requireNonNull(commandPrefix, "commandPrefix");
        if (commandPrefix.isEmpty()) {
            throw new IllegalArgumentException("commandPrefix must not be empty");
        }
        commandPrefix = List.copyOf(commandPrefix);
    }

    public static CPythonWorkerCommand forExecutable(Path pythonExecutable) {
        Objects.requireNonNull(pythonExecutable, "pythonExecutable");
        return new CPythonWorkerCommand(List.of(pythonExecutable.toString()));
    }

    List<String> commandFor(Path script) {
        Objects.requireNonNull(script, "script");
        List<String> command = new ArrayList<>(commandPrefix);
        command.add(script.toString());
        return List.copyOf(command);
    }

    List<String> commandFor(Path launcher, Path script) {
        Objects.requireNonNull(launcher, "launcher");
        Objects.requireNonNull(script, "script");
        List<String> command = new ArrayList<>(commandPrefix);
        command.add(launcher.toString());
        command.add(script.toString());
        return List.copyOf(command);
    }
}
