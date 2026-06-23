package com.pythonburp.python;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

final class PowerShellRuntimeProvisioner implements RuntimeProvisioner {
    private static final String USERS_SID = "*S-1-5-32-545";
    private static final Path SHARED_STATUS_DIRECTORY =
        Path.of(
            System.getenv("PUBLIC") == null || System.getenv("PUBLIC").isBlank()
                ? "C:\\Users\\Public"
                : System.getenv("PUBLIC"),
            "BurpPythonIDE");
    private final ProcessLauncher launcher;

    PowerShellRuntimeProvisioner() {
        this(new DefaultProcessLauncher());
    }

    PowerShellRuntimeProvisioner(ProcessLauncher launcher) {
        this.launcher = Objects.requireNonNull(launcher, "launcher");
    }

    @Override
    public void provision(Path runtimeRoot) throws IOException, InterruptedException {
        Files.createDirectories(sharedStatusDirectory());
        Path script = Files.createTempFile(sharedStatusDirectory(), "provision-script-", ".ps1");
        Path statusFile = Files.createTempFile(sharedStatusDirectory(), "provision-status-", ".txt");
        Path logFile = Files.createTempFile(sharedStatusDirectory(), "provision-log-", ".txt");
        boolean success = false;
        try {
            Files.writeString(script, helperScript(), StandardCharsets.UTF_8);
            Files.writeString(
                logFile,
                """
                Java parent provisioning bootstrap
                Script: %s
                TargetDir: %s
                StatusFile: %s
                """.formatted(script, runtimeRoot, statusFile),
                StandardCharsets.UTF_8);
            ProcessLaunchResult result = launcher.launch(script, runtimeRoot, statusFile, logFile);
            if (!result.output().isBlank()) {
                Files.writeString(
                    logFile,
                    System.lineSeparator() + "Outer process output: " + result.output().trim() + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);
            }
            int exitCode = result.exitCode();
            String status = readStatus(statusFile);
            if (exitCode != 0) {
                String details = status.isBlank() ? result.output().trim() : status;
                throw new IOException(
                    details.isBlank()
                        ? "Administrator provisioning exited with code "
                            + exitCode
                            + ". Diagnostics: "
                            + logFile
                        : "Administrator provisioning exited with code "
                            + exitCode
                            + ": "
                            + details
                            + ". Diagnostics: "
                            + logFile);
            }
            if (!status.isBlank() && !"OK".equals(status)) {
                throw new IOException(
                    "Administrator provisioning reported unexpected status: "
                        + status
                        + ". Diagnostics: "
                        + logFile);
            }
            success = true;
        } finally {
            if (success) {
                Files.deleteIfExists(script);
                Files.deleteIfExists(statusFile);
                Files.deleteIfExists(logFile);
            }
        }
    }

    private static String helperScript() {
        return """
            param(
                [Parameter(Mandatory = $true)]
                [string]$TargetDir,
                [Parameter(Mandatory = $true)]
                [string]$StatusFile,
                [Parameter(Mandatory = $true)]
                [string]$LogFile
            )
            $ErrorActionPreference = 'Stop'
            try {
                Set-Content -Path $LogFile -Value ("Starting provisioning for " + $TargetDir) -Encoding UTF8
                New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
                Add-Content -Path $LogFile -Value 'Created or confirmed runtime directory.'
                $icaclsOutput = & icacls.exe $TargetDir /grant '%s:(OI)(CI)M' /c 2>&1
                if ($LASTEXITCODE -ne 0) {
                    throw ('icacls failed: ' + (($icaclsOutput | Out-String).Trim()))
                }
                Add-Content -Path $LogFile -Value 'icacls completed successfully.'
                Set-Content -Path $StatusFile -Value 'OK' -Encoding UTF8
                exit 0
            } catch {
                $message = $_.Exception.Message
                if ([string]::IsNullOrWhiteSpace($message)) {
                    $message = ($_ | Out-String).Trim()
                }
                Add-Content -Path $LogFile -Value $message
                Set-Content -Path $StatusFile -Value $message -Encoding UTF8
                exit 1
            }
            """.formatted(USERS_SID);
    }

    private static String startProcessCommand(Path script, Path runtimeRoot, Path statusFile, Path logFile) {
        return "$ErrorActionPreference = 'Stop'; "
            + "$proc = Start-Process -FilePath 'powershell.exe' -Verb RunAs -PassThru -Wait "
            + "-ArgumentList @("
            + "'-NoProfile',"
            + "'-ExecutionPolicy','Bypass',"
            + "'-File','" + psQuote(script.toString()) + "',"
            + "'-TargetDir','" + psQuote(runtimeRoot.toString()) + "',"
            + "'-StatusFile','" + psQuote(statusFile.toString()) + "',"
            + "'-LogFile','" + psQuote(logFile.toString()) + "'"
            + "); "
            + "exit $proc.ExitCode";
    }

    private static String readStatus(Path statusFile) throws IOException {
        if (!Files.exists(statusFile)) {
            return "";
        }
        return Files.readString(statusFile, StandardCharsets.UTF_8).trim();
    }

    static Path sharedStatusDirectory() {
        return SHARED_STATUS_DIRECTORY;
    }

    private static String psQuote(String value) {
        return value.replace("'", "''");
    }

    interface ProcessLauncher {
        ProcessLaunchResult launch(Path script, Path targetDir, Path statusFile, Path logFile)
            throws IOException, InterruptedException;
    }

    private static final class DefaultProcessLauncher implements ProcessLauncher {
        @Override
        public ProcessLaunchResult launch(Path script, Path targetDir, Path statusFile, Path logFile)
            throws IOException, InterruptedException {
            Process process = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                startProcessCommand(script, targetDir, statusFile, logFile))
                .redirectErrorStream(true)
                .start();
            int exitCode = process.waitFor();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return new ProcessLaunchResult(exitCode, output);
        }
    }
}
