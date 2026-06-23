package com.pythonburp.packages;

import com.pythonburp.storage.ExtensionDataPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PipCommandFactory {
    private final Path python;
    private final ExtensionDataPaths paths;

    public PipCommandFactory(Path python, ExtensionDataPaths paths) {
        this.python = python.toAbsolutePath().normalize();
        this.paths = paths;
    }

    public List<String> installRequirement(String requirement, Path target, PackageManagerSettings settings) {
        List<String> command = base(target, settings);
        command.add(requirement);
        return List.copyOf(command);
    }

    public List<String> installWheel(Path wheel, Path target, PackageManagerSettings settings) {
        List<String> command = base(target, settings);
        command.add(wheel.toAbsolutePath().normalize().toString());
        return List.copyOf(command);
    }

    public List<String> installRequirements(Path requirements, Path target, PackageManagerSettings settings) {
        List<String> command = base(target, settings);
        command.add("-r");
        command.add(requirements.toAbsolutePath().normalize().toString());
        return List.copyOf(command);
    }

    private List<String> base(Path target, PackageManagerSettings settings) {
        List<String> command = new ArrayList<>(List.of(
            python.toString(), "-m", "pip", "install", "--upgrade",
            "--target", target.toAbsolutePath().normalize().toString(),
            "--cache-dir", paths.pipCache().toString(),
            "--disable-pip-version-check", "--no-input",
            "--timeout", Integer.toString(settings.timeoutSeconds())
        ));
        append(command, "--index-url", settings.indexUrl());
        append(command, "--extra-index-url", settings.extraIndexUrl());
        append(command, "--proxy", settings.proxyUrl());
        for (String host : settings.trustedHosts()) append(command, "--trusted-host", host);
        return command;
    }

    private static void append(List<String> command, String option, String value) {
        if (value != null && !value.isBlank()) {
            command.add(option);
            command.add(value);
        }
    }
}
