package com.pythonburp.python;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PowerShellRuntimeProvisionerTest {
    @TempDir
    Path tempDir;

    @Test
    void surfacesDetailedStatusFileContentWhenElevatedProcessFails() throws Exception {
        Path runtimeRoot = tempDir.resolve("Nmap/zenmap/bin/BurpPythonIDE");
        TestLauncher launcher = new TestLauncher((script, targetDir, statusFile, logFile) -> {
            Files.writeString(statusFile, "icacls failed: Access is denied.", StandardCharsets.UTF_8);
            return new ProcessLaunchResult(1, "");
        });

        IOException error =
            assertThrows(
                IOException.class,
                () -> new PowerShellRuntimeProvisioner(launcher).provision(runtimeRoot));

        assertTrue(error.getMessage().contains("icacls failed: Access is denied."));
        assertTrue(Files.exists(launcher.recordedScript));
        assertTrue(Files.exists(launcher.recordedStatusFile));
    }

    @Test
    void acceptsSuccessfulProvisioningWhenStatusFileReportsOk() throws Exception {
        Path runtimeRoot = tempDir.resolve("Nmap/zenmap/bin/BurpPythonIDE");
        TestLauncher launcher = new TestLauncher((script, targetDir, statusFile, logFile) -> {
            Files.writeString(statusFile, "OK", StandardCharsets.UTF_8);
            return new ProcessLaunchResult(0, "");
        });

        new PowerShellRuntimeProvisioner(launcher).provision(runtimeRoot);

        assertEquals(runtimeRoot, launcher.recordedTargetDir);
        assertTrue(launcher.recordedScript != null);
        assertTrue(
            launcher.recordedScript.startsWith(PowerShellRuntimeProvisioner.sharedStatusDirectory()),
            launcher.recordedScript.toString());
        assertTrue(
            launcher.recordedStatusFile.startsWith(PowerShellRuntimeProvisioner.sharedStatusDirectory()),
            launcher.recordedStatusFile.toString());
    }

    @Test
    void fallsBackToOuterProcessOutputWhenStatusFileIsMissing() throws Exception {
        Path runtimeRoot = tempDir.resolve("Nmap/zenmap/bin/BurpPythonIDE");
        TestLauncher launcher =
            new TestLauncher((script, targetDir, statusFile, logFile) -> new ProcessLaunchResult(1, "UAC helper failed"));

        IOException error =
            assertThrows(
                IOException.class,
                () -> new PowerShellRuntimeProvisioner(launcher).provision(runtimeRoot));

        assertTrue(error.getMessage().contains("UAC helper failed"));
        assertTrue(error.getMessage().contains("Diagnostics:"));
        List<String> logLines = Files.readAllLines(launcher.recordedLogFile, StandardCharsets.UTF_8);
        assertTrue(logLines.stream().anyMatch(line -> line.contains("Outer process output: UAC helper failed")));
    }

    @FunctionalInterface
    private interface LauncherCallback {
        ProcessLaunchResult launch(Path script, Path targetDir, Path statusFile, Path logFile)
            throws IOException, InterruptedException;
    }

    private static final class TestLauncher implements PowerShellRuntimeProvisioner.ProcessLauncher {
        private final LauncherCallback callback;
        private Path recordedScript;
        private Path recordedTargetDir;
        private Path recordedStatusFile;
        private Path recordedLogFile;

        private TestLauncher(LauncherCallback callback) {
            this.callback = callback;
        }

        @Override
        public ProcessLaunchResult launch(Path script, Path targetDir, Path statusFile, Path logFile)
            throws IOException, InterruptedException {
            recordedScript = script;
            recordedTargetDir = targetDir;
            recordedStatusFile = statusFile;
            recordedLogFile = logFile;
            return callback.launch(script, targetDir, statusFile, logFile);
        }
    }
}
