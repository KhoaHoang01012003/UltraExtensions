package com.pythonburp.python;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CPythonWorkerRuntimeTest {
    @TempDir
    Path tempDir;

    @Test
    void capturesStdoutFromWorkerProcess() throws Exception {
        CPythonWorkerRuntime runtime = runtimeWithFakeInterpreter("""
            Write-Output "3"
            exit 0
            """);

        ScriptRunResult result = runtime.execute("print(1 + 2)", Duration.ofSeconds(5));

        assertEquals(ScriptStatus.SUCCEEDED, result.status(), result.errorMessage());
        assertTrue(result.stdout().contains("3"));
    }

    @Test
    void returnsFailureWhenWorkerExitsNonZero() throws Exception {
        CPythonWorkerRuntime runtime = runtimeWithFakeInterpreter("""
            Write-Error "boom"
            exit 7
            """);

        ScriptRunResult result = runtime.execute("raise Exception('boom')", Duration.ofSeconds(5));

        assertEquals(ScriptStatus.FAILED, result.status());
        assertTrue(result.stderr().contains("boom"));
        assertTrue(result.errorMessage().contains("exit code 7"));
    }

    @Test
    void killsWorkerWhenScriptTimesOut() throws Exception {
        CPythonWorkerRuntime runtime = runtimeWithFakeInterpreter("""
            Start-Sleep -Seconds 30
            exit 0
            """);

        ScriptRunResult result = runtime.execute("while True:\n    pass", Duration.ofMillis(200));

        assertEquals(ScriptStatus.FAILED, result.status());
        assertTrue(result.errorMessage().contains("timed out"));
    }

    private CPythonWorkerRuntime runtimeWithFakeInterpreter(String body) throws Exception {
        Path fake = tempDir.resolve("fake-python.ps1");
        Files.writeString(fake, """
            $scriptPath = $args[0]
            if (-not (Test-Path $scriptPath)) {
                Write-Error "missing script file"
                exit 9
            }
            """ + body);
        return new CPythonWorkerRuntime(
            new CPythonWorkerCommand(List.of(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                fake.toString()
            )),
            tempDir.resolve("work")
        );
    }
}
