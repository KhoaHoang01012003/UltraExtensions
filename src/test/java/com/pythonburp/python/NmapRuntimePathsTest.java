package com.pythonburp.python;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NmapRuntimePathsTest {
  @TempDir Path tempDir;

  @Test
  void resolvesWorkerCacheBelowBurpSubdirectory() throws Exception {
    Path nmapBin = Files.createDirectories(tempDir.resolve("Nmap/zenmap/bin"));

    Path runtimeCacheRoot = new NmapRuntimePaths(nmapBin).workerCacheRoot();

    assertEquals(
        nmapBin.resolve("BurpPythonIDE").resolve("cpython-worker").normalize(), runtimeCacheRoot);
    assertTrue(Files.isDirectory(runtimeCacheRoot));
  }

  @Test
  void rejectsMissingZenmapBinDirectory() {
    IOException error =
        assertThrows(
            IOException.class,
            () -> new NmapRuntimePaths(tempDir.resolve("missing/zenmap/bin")).workerCacheRoot());

    assertTrue(error.getMessage().contains("Nmap"));
  }

  @Test
  void rejectsFileInsteadOfZenmapBinDirectory() throws Exception {
    Path file = Files.writeString(tempDir.resolve("zenmap-bin.txt"), "not-a-directory");

    IOException error =
        assertThrows(IOException.class, () -> new NmapRuntimePaths(file).workerCacheRoot());

    assertTrue(error.getMessage().contains("directory"));
  }
}
