package com.pythonburp.python;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pythonburp.storage.ExtensionDataPaths;
import java.nio.file.Files;
import java.nio.file.Path;
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
  void keepsUserPackagesUnderExtensionDataRoot() {
    ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("localappdata/BurpPythonIDE"));

    assertEquals(
        paths.root().resolve("packages/cpython-3.12-windows-x64").normalize(),
        paths.userPackages().normalize());
  }

  @Test
  void wrapsMissingNmapDirectoryWithActionableMessage() {
    ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("localappdata/BurpPythonIDE"));
    CPythonRuntimeFactory factory =
        new CPythonRuntimeFactory(new NmapRuntimePaths(tempDir.resolve("missing/zenmap/bin")), paths);

    IllegalStateException error =
        assertThrows(IllegalStateException.class, factory::pythonExecutable);

    assertTrue(error.getMessage().contains("embedded CPython runtime"));
    assertTrue(error.getCause().getMessage().contains("Nmap"));
  }
}
