package com.pythonburp.catalog;

import com.pythonburp.python.CPythonRuntimeFactory;
import com.pythonburp.python.PythonRuntime;
import com.pythonburp.python.ScriptRunResult;
import com.pythonburp.python.ScriptStatus;
import com.pythonburp.storage.ExtensionDataPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class PackageSmokeTest {
  @TempDir Path tempDir;

  @Test
  void bundledSmokeTestsPass() throws Exception {
    PackageCatalog catalog = PackageCatalogLoader.loadBundled();
    Path nmapBin = Files.createDirectories(tempDir.resolve("Nmap/zenmap/bin"));
    ExtensionDataPaths paths =
        new ExtensionDataPaths(tempDir.resolve("localappdata/BurpPythonIDE"));
    StringBuilder failures = new StringBuilder();
    try (PythonRuntime runtime = new CPythonRuntimeFactory(nmapBin, paths).get()) {
      for (PackageCatalogEntry entry : catalog.entries()) {
        ScriptRunResult result = runtime.execute(entry.smokeTest(), Duration.ofSeconds(30));
        if (result.status() != ScriptStatus.SUCCEEDED) {
          failures.append(entry.name())
              .append(" failed: ")
              .append(result.errorMessage())
              .append(System.lineSeparator());
        }
      }
    }
    assertTrue(failures.isEmpty(), failures.toString());
  }
}
