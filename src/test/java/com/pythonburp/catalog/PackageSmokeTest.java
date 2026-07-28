package com.pythonburp.catalog;

import com.pythonburp.python.CPythonRuntimeFactory;
import com.pythonburp.python.PythonRuntime;
import com.pythonburp.python.ScriptRunRequest;
import com.pythonburp.python.ScriptRunResult;
import com.pythonburp.python.ScriptStatus;
import com.pythonburp.storage.ExtensionDataPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PackageSmokeTest {
  @TempDir Path tempDir;

  @Test
  void bundledSmokeTestsPass() throws Exception {
    Path nmapBin = Path.of(System.getProperty("burpPythonTestZenmapBin")).toAbsolutePath().normalize();
    assertTrue(Files.isRegularFile(nmapBin.resolve("python.exe")), "Missing test Zenmap Python at " + nmapBin);
    ExtensionDataPaths paths =
        new ExtensionDataPaths(tempDir.resolve("localappdata/BurpPythonIDE"));
    try (PythonRuntime runtime = new CPythonRuntimeFactory(nmapBin, paths).get()) {
      ScriptRunResult sslResult =
          runtime.execute(
              "import _socket, colorsys, hashlib, logging.handlers, select, socket, ssl, sys, unicodedata, urllib.request\n"
                  + "print(sys.executable)\n"
                  + "print(colorsys.__name__)\n"
                  + "print(ssl.OPENSSL_VERSION)\n"
                  + "with urllib.request.urlopen('https://pypi.org/simple/pip/', timeout=20) as response:\n"
                  + "    print(response.status)",
              Duration.ofSeconds(60));
      assertEquals(ScriptStatus.SUCCEEDED, sslResult.status(), sslResult.stderr() + sslResult.errorMessage());
      assertTrue(sslResult.stdout().contains(nmapBin.resolve("python.exe").toString()));
      assertTrue(sslResult.stdout().contains("colorsys"));
      assertTrue(sslResult.stdout().contains("OpenSSL"));
      assertTrue(sslResult.stdout().contains("200"));

      ScriptRunResult helperResult =
          runtime.execute(
              "from burp import crypto; print(crypto.sha256_hex(b'abc'))",
              Duration.ofSeconds(30));
      assertEquals(
          ScriptStatus.SUCCEEDED,
          helperResult.status(),
          helperResult.stderr() + helperResult.errorMessage());
      assertTrue(helperResult.stdout().contains("ba7816bf"));

      Path downloadDirectory = Files.createDirectories(tempDir.resolve("pip-download"));
      ScriptRunResult pipResult = runtime.execute(ScriptRunRequest.customCommand(
          "-m pip download --disable-pip-version-check --no-deps --dest \""
              + downloadDirectory + "\" pip",
          Duration.ofSeconds(120)
      ));
      assertEquals(ScriptStatus.SUCCEEDED, pipResult.status(), pipResult.stderr() + pipResult.errorMessage());
      try (var downloads = Files.list(downloadDirectory)) {
        assertTrue(downloads.anyMatch(path -> path.getFileName().toString().startsWith("pip-")
            && path.getFileName().toString().endsWith(".whl")));
      }
    }
  }
}
