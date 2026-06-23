package com.pythonburp.python;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class PowerShellRuntimeProvisioner implements RuntimeProvisioner {
    private static final String USERS_SID = "*S-1-5-32-545";

    @Override
    public void provision(Path runtimeRoot) throws IOException, InterruptedException {
        Path script = Files.createTempFile("burp-python-provision-", ".ps1");
        try {
            Files.writeString(script, helperScript(), StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                startProcessCommand(script, runtimeRoot))
                .redirectErrorStream(true)
                .start();
            int exitCode = process.waitFor();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (exitCode != 0) {
                throw new IOException(
                    output.isBlank()
                        ? "Administrator provisioning exited with code " + exitCode
                        : "Administrator provisioning exited with code " + exitCode + ": " + output);
            }
        } finally {
            Files.deleteIfExists(script);
        }
    }

    private static String helperScript() {
        return """
            param(
                [Parameter(Mandatory = $true)]
                [string]$TargetDir
            )
            $ErrorActionPreference = 'Stop'
            New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
            & icacls $TargetDir /grant '%s:(OI)(CI)M' /c | Out-Null
            """.formatted(USERS_SID);
    }

    private static String startProcessCommand(Path script, Path runtimeRoot) {
        return "$ErrorActionPreference = 'Stop'; "
            + "$proc = Start-Process -FilePath 'powershell.exe' -Verb RunAs -PassThru -Wait "
            + "-ArgumentList @("
            + "'-NoProfile',"
            + "'-ExecutionPolicy','Bypass',"
            + "'-File','" + psQuote(script.toString()) + "',"
            + "'-TargetDir','" + psQuote(runtimeRoot.toString()) + "'"
            + "); "
            + "exit $proc.ExitCode";
    }

    private static String psQuote(String value) {
        return value.replace("'", "''");
    }
}
