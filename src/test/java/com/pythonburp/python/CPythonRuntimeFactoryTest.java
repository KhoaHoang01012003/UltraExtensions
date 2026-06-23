package com.pythonburp.python;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pythonburp.storage.ExtensionDataPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CPythonRuntimeFactoryTest {
  @TempDir Path tempDir;

  @Test
  void extractsPythonExecutableBeneathInjectedNmapBinRoot() throws Exception {
    Path nmapBin = Files.createDirectories(tempDir.resolve("Nmap/zenmap/bin"));
    ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("localappdata/BurpPythonIDE"));
    CPythonRuntimeFactory factory = new CPythonRuntimeFactory(new NmapRuntimePaths(nmapBin), paths);

    Path pythonExecutable = factory.pythonExecutable();

    assertEquals("python.exe", pythonExecutable.getFileName().toString());
    assertTrue(
        pythonExecutable.startsWith(
            nmapBin.resolve("BurpPythonIDE").resolve("cpython-worker").normalize()));
    assertTrue(
        pythonExecutable.toString().contains(CPythonRuntimeFactory.RUNTIME_ID));
    assertTrue(Files.exists(pythonExecutable));
  }

  @Test
  void getExposesUserPackagesUnderExtensionDataRoot() throws Exception {
    Path nmapBin = Files.createDirectories(tempDir.resolve("Nmap/zenmap/bin"));
    ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("localappdata/BurpPythonIDE"));
    CPythonRuntimeFactory factory = new CPythonRuntimeFactory(new NmapRuntimePaths(nmapBin), paths);

    try (PythonRuntime runtime = factory.get(new com.pythonburp.bridge.BurpBridge())) {
      ScriptRunResult result =
          runtime.execute(
              "import os\nprint(os.environ.get('BURP_PYTHON_USER_PACKAGES'))\n",
              Duration.ofSeconds(10));

      assertEquals(ScriptStatus.SUCCEEDED, result.status(), result.errorMessage());
      assertTrue(
          result.stdout().contains(paths.userPackages().toAbsolutePath().normalize().toString()));
    }
  }

  @Test
  void wrapsMissingNmapDirectoryWithActionableMessageForPythonExecutable() {
    ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("localappdata/BurpPythonIDE"));
    CPythonRuntimeFactory factory =
        new CPythonRuntimeFactory(new NmapRuntimePaths(tempDir.resolve("missing/zenmap/bin")), paths);

    IllegalStateException error =
        assertThrows(IllegalStateException.class, factory::pythonExecutable);

    assertTrue(error.getMessage().contains("embedded CPython runtime"));
    assertTrue(error.getCause().getMessage().contains("Nmap"));
  }

  @Test
  void wrapsMissingNmapDirectoryWithActionableMessageForGet() {
    ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("localappdata/BurpPythonIDE"));
    CPythonRuntimeFactory factory =
        new CPythonRuntimeFactory(new NmapRuntimePaths(tempDir.resolve("missing/zenmap/bin")), paths);

    IllegalStateException error =
        assertThrows(IllegalStateException.class, () -> factory.get(new com.pythonburp.bridge.BurpBridge()));

    assertTrue(error.getMessage().contains("embedded CPython runtime"));
    assertTrue(error.getCause().getMessage().contains("Nmap"));
  }

  @Test
  void rejectsNullConstructorDependencies() {
    ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("localappdata/BurpPythonIDE"));
    NmapRuntimePaths runtimePaths =
        new NmapRuntimePaths(tempDir.resolve("Nmap/zenmap/bin"));

    assertThrows(NullPointerException.class, () -> new CPythonRuntimeFactory(null, paths));
    assertThrows(NullPointerException.class, () -> new CPythonRuntimeFactory(runtimePaths, null));
  }
}
