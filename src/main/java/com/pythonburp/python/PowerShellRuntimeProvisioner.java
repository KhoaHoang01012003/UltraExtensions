package com.pythonburp.python;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

final class PowerShellRuntimeProvisioner implements RuntimeProvisioner {
    private static final String USERS_SID = "*S-1-5-32-545";
    private final ProcessLauncher launcher;

    PowerShellRuntimeProvisioner() {
        this(new DefaultProcessLauncher());
    }

    PowerShellRuntimeProvisioner(ProcessLauncher launcher) {
        this.launcher = Objects.requireNonNull(launcher, "launcher");
    }

    @Override
    public void provision(Path runtimeRoot) throws IOException, InterruptedException {
        Path script = Files.createTempFile("burp-python-provision-", ".ps1");
        Path statusFile = Files.createTempFile("burp-python-provision-status-", ".txt");
        try {
            Files.writeString(script, helperScript(), StandardCharsets.UTF_8);
            ProcessLaunchResult result = launcher.launch(script, runtimeRoot, statusFile);
            int exitCode = result.exitCode();
            String status = readStatus(statusFile);
            if (exitCode != 0) {
                String details = status.isBlank() ? result.output().trim() : status;
                throw new IOException(
                    details.isBlank()
                        ? "Administrator provisioning exited with code " + exitCode
                        : "Administrator provisioning exited with code " + exitCode + ": " + details);
            }
            if (!status.isBlank() && !"OK".equals(status)) {
                throw new IOException("Administrator provisioning reported unexpected status: " + status);
            }
        } finally {
            Files.deleteIfExists(script);
            Files.deleteIfExists(statusFile);
        }
    }

    private static String helperScript() {
        return """
            param(
                [Parameter(Mandatory = $true)]
                [string]$TargetDir,
                [Parameter(Mandatory = $true)]
                [string]$StatusFile
            )
            $ErrorActionPreference = 'Stop'
            try {
                New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
                $icaclsOutput = & icacls.exe $TargetDir /grant '%s:(OI)(CI)M' /c 2>&1
                if ($LASTEXITCODE -ne 0) {
                    throw ('icacls failed: ' + (($icaclsOutput | Out-String).Trim()))
                }
                Set-Content -Path $StatusFile -Value 'OK' -Encoding UTF8
                exit 0
            } catch {
                $message = $_.Exception.Message
                if ([string]::IsNullOrWhiteSpace($message)) {
                    $message = ($_ | Out-String).Trim()
                }
                Set-Content -Path $StatusFile -Value $message -Encoding UTF8
                exit 1
            }
            """.formatted(USERS_SID);
    }

    private static String startProcessCommand(Path script, Path runtimeRoot, Path statusFile) {
        return "$ErrorActionPreference = 'Stop'; "
            + "$proc = Start-Process -FilePath 'powershell.exe' -Verb RunAs -PassThru -Wait "
            + "-ArgumentList @("
            + "'-NoProfile',"
            + "'-ExecutionPolicy','Bypass',"
            + "'-File','" + psQuote(script.toString()) + "',"
            + "'-TargetDir','" + psQuote(runtimeRoot.toString()) + "',"
            + "'-StatusFile','" + psQuote(statusFile.toString()) + "'"
            + "); "
            + "exit $proc.ExitCode";
    }

    private static String readStatus(Path statusFile) throws IOException {
        if (!Files.exists(statusFile)) {
            return "";
        }
        return Files.readString(statusFile, StandardCharsets.UTF_8).trim();
    }

    private static String psQuote(String value) {
        return value.replace("'", "''");
    }

    interface ProcessLauncher {
        ProcessLaunchResult launch(Path script, Path targetDir, Path statusFile)
            throws IOException, InterruptedException;
    }

    private static final class DefaultProcessLauncher implements ProcessLauncher {
        @Override
        public ProcessLaunchResult launch(Path script, Path targetDir, Path statusFile)
            throws IOException, InterruptedException {
            Process process = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                startProcessCommand(script, targetDir, statusFile))
                .redirectErrorStream(true)
                .start();
            int exitCode = process.waitFor();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return new ProcessLaunchResult(exitCode, output);
        }
    }
}
