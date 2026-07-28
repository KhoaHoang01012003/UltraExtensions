package com.pythonburp.python;

import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.bridge.HttpBridge;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CPythonWorkerHttpBridgeTest {
  @TempDir Path tempDir;

  @Test
  void helperRuntimeCanServeBurpHttpRequestsWithoutEmbeddedInterpreter() throws Exception {
    BurpBridge bridge =
        new BurpBridge(
            (method, url, body) -> {
              assertEquals("POST", method);
              assertEquals("https://target.example/api", url);
              assertEquals("payload", body);
              return new HttpBridge.HttpResult(202, "accepted");
            });
    Path fake = tempDir.resolve("fake-python-http.ps1");
    Files.writeString(fake, """
        $rpc = $env:BURP_PYTHON_RPC_DIR
        $request = Join-Path $rpc "1.request"
        $response = Join-Path $rpc "1.response"
        Set-Content -Path $request -Encoding UTF8 -Value @(
            "operation=http.send",
            "method=POST",
            "url=https://target.example/api",
            "body=payload",
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
    try (PythonRuntime runtime = new CPythonWorkerRuntime(
        new CPythonWorkerCommand(List.of(
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            fake.toString()
        )),
        tempDir.resolve("work"),
        bridge,
        tempDir.resolve("user-packages"),
        tempDir.resolve("helper-root"),
        InteractiveInputHandler.disabled()
    )) {
      ScriptRunResult result =
          runtime.execute(
              "ignored by fake interpreter",
              Duration.ofSeconds(30));

      assertEquals(
          ScriptStatus.SUCCEEDED,
          result.status(),
          result.errorMessage() + "\nSTDERR:\n" + result.stderr());
      assertTrue(result.stdout().contains("statusCode=202"));
      assertTrue(result.stdout().contains("body=accepted"));
    }
  }
}
