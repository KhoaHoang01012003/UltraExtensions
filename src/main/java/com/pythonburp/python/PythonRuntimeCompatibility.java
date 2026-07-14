package com.pythonburp.python;

import java.util.Locale;

final class PythonRuntimeCompatibility {
    static final int SUPPORTED_MAJOR = 3;
    static final int SUPPORTED_MINOR = 14;
    static final int SUPPORTED_MICRO = 3;

    private PythonRuntimeCompatibility() {
    }

    static void validate(int major, int minor, int micro, String platform, String architecture, String runtimeDetails) {
        String version = major + "." + minor + "." + micro;
        if (major != SUPPORTED_MAJOR || minor != SUPPORTED_MINOR || micro != SUPPORTED_MICRO) {
            throw new IllegalStateException(
                "The bundled compatibility runtime requires Zenmap Python 3.14.3, but detected " + version + "."
            );
        }
        if (!"windows".equalsIgnoreCase(platform)) {
            throw new IllegalStateException("The bundled compatibility runtime supports Windows only, but detected " + platform + ".");
        }
        String normalizedArchitecture = architecture == null ? "" : architecture.toLowerCase(Locale.ROOT);
        if (!normalizedArchitecture.equals("amd64") && !normalizedArchitecture.equals("x86_64")) {
            throw new IllegalStateException(
                "The bundled compatibility runtime requires Windows x64, but detected " + architecture + "."
            );
        }
        String normalizedRuntimeDetails = runtimeDetails == null ? "" : runtimeDetails.toLowerCase(Locale.ROOT);
        if (!normalizedRuntimeDetails.contains("mingw gcc 15.2.0")) {
            throw new IllegalStateException(
                "The bundled compatibility runtime requires the Nmap 7.99 MinGW CPython build, but detected: "
                    + runtimeDetails
            );
        }
    }
}
