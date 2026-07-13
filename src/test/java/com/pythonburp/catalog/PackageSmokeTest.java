package com.pythonburp.catalog;

import com.pythonburp.python.CPythonRuntimeFactory;
import com.pythonburp.python.PythonRuntime;
import com.pythonburp.python.ScriptRunResult;
import com.pythonburp.python.ScriptStatus;
import com.pythonburp.storage.ExtensionDataPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PackageSmokeTest {
  @TempDir Path tempDir;

  @Test
  void bundledSmokeTestsPass() throws Exception {
    Path nmapBin = Path.of("C:", "Program Files (x86)", "Nmap", "zenmap", "bin");
    Assumptions.assumeTrue(
        Files.isRegularFile(nmapBin.resolve("python.exe")),
        "Zenmap Python is not available for package smoke test");
    ExtensionDataPaths paths =
        new ExtensionDataPaths(tempDir.resolve("localappdata/BurpPythonIDE"));
    try (PythonRuntime runtime = new CPythonRuntimeFactory(nmapBin, paths).get()) {
      ScriptRunResult sslResult =
          runtime.execute(
              "import colorsys, logging.handlers, ssl; print(colorsys.__name__); print(ssl.OPENSSL_VERSION)",
              Duration.ofSeconds(30));
      assertEquals(ScriptStatus.SUCCEEDED, sslResult.status(), sslResult.stderr() + sslResult.errorMessage());
      assertTrue(sslResult.stdout().contains("colorsys"));
      assertTrue(sslResult.stdout().contains("OpenSSL"));

      ScriptRunResult helperResult =
          runtime.execute(
              "from burp import crypto; print(crypto.sha256_hex(b'abc'))",
              Duration.ofSeconds(30));
      assertEquals(
          ScriptStatus.SUCCEEDED,
          helperResult.status(),
          helperResult.stderr() + helperResult.errorMessage());
      assertTrue(helperResult.stdout().contains("ba7816bf"));
    }
  }
}
