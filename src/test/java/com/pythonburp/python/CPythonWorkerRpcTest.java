package com.pythonburp.python;

import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.bridge.HttpBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CPythonWorkerRpcTest {
    @TempDir
    Path tempDir;

    @Test
    void dispatchesHttpSendRequestsFromWorkerProcess() throws Exception {
        Path fake = tempDir.resolve("fake-python-rpc.ps1");
        Files.writeString(fake, """
            $rpc = $env:BURP_PYTHON_RPC_DIR
            if (-not $rpc) {
                Write-Error "missing rpc dir"
                exit 2
            }
            $request = Join-Path $rpc "1.request"
            $response = Join-Path $rpc "1.response"
            Set-Content -Path $request -Encoding UTF8 -Value @(
                "operation=http.send",
                "method=POST",
                "url=https://target.example/api",
                "body=hello",
                "__end=1"
            )
            $deadline = (Get-Date).AddSeconds(10)
            while (-not (Test-Path $response)) {
                if ((Get-Date) -gt $deadline) {
                    Write-Error "timed out waiting for response"
                    exit 3
                }
                Start-Sleep -Milliseconds 50
            }
            Get-Content $response
            exit 0
            """);
        BurpBridge bridge = new BurpBridge((method, url, body) -> {
            assertEquals("POST", method);
            assertEquals("https://target.example/api", url);
            assertEquals("hello", body);
            return new HttpBridge.HttpResult(201, "created");
        });
        CPythonWorkerRuntime runtime = new CPythonWorkerRuntime(
            new CPythonWorkerCommand(List.of(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                fake.toString()
            )),
            tempDir.resolve("work"),
            bridge
        );

        ScriptRunResult result = runtime.execute("ignored by fake interpreter", Duration.ofSeconds(15));

        assertEquals(ScriptStatus.SUCCEEDED, result.status(), result.errorMessage());
        assertTrue(result.stdout().contains("statusCode=201"), result.stdout());
        assertTrue(result.stdout().contains("body=created"), result.stdout());
    }
}
