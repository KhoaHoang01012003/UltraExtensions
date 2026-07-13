package com.pythonburp.python;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public record PythonRuntimeEnvironment(
    Path executable,
    int major,
    int minor,
    int micro,
    String platform,
    String architecture,
    boolean pipAvailable
) {
    public PythonRuntimeEnvironment {
        executable = Objects.requireNonNull(executable, "executable").toAbsolutePath().normalize();
        platform = blankTo(platform, "windows");
        architecture = blankTo(architecture, "x64");
    }

    public String version() {
        return major + "." + minor + "." + micro;
    }

    public String environmentKey() {
        return "python-" + major + "." + minor + "-" + normalize(platform) + "-" + normalizeArchitecture(architecture);
    }

    public boolean isPython3() {
        return major == 3;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    private static String normalizeArchitecture(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "amd64", "x86_64", "x64" -> "x64";
            case "x86", "i386", "i686" -> "x86";
            case "arm64", "aarch64" -> "arm64";
            default -> normalize(value);
        };
    }
}
