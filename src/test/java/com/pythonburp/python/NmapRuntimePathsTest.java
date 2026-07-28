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
  void normalizesZenmapBinPathInConstructor() throws Exception {
    Path normalizedNmapBin = Files.createDirectories(tempDir.resolve("Nmap/zenmap/bin"));
    Path nmapBinWithDotSegments = normalizedNmapBin.resolve("../bin/./");

    Path runtimeCacheRoot = new NmapRuntimePaths(nmapBinWithDotSegments).workerCacheRoot();

    assertEquals(
        normalizedNmapBin.resolve("BurpPythonIDE").resolve("cpython-worker").normalize(),
        runtimeCacheRoot);
  }

  @Test
  void createsMissingZenmapBinTreeWhenNmapInstallationRootExists() throws Exception {
    Path nmapRoot = Files.createDirectories(tempDir.resolve("Nmap"));
    Path missingZenmapBin = nmapRoot.resolve("zenmap/bin");

    Path runtimeCacheRoot = new NmapRuntimePaths(missingZenmapBin).workerCacheRoot();

    assertTrue(Files.isDirectory(missingZenmapBin));
    assertEquals(
        missingZenmapBin.resolve("BurpPythonIDE").resolve("cpython-worker").normalize(),
        runtimeCacheRoot);
    assertTrue(Files.isDirectory(runtimeCacheRoot));
  }

  @Test
  void createsMissingZenmapBinTreeForMixedCaseWindowsStyleSuffix() throws Exception {
    Path nmapRoot = Files.createDirectories(tempDir.resolve("Nmap"));
    Path missingZenmapBin = nmapRoot.resolve("Zenmap/Bin");

    Path runtimeCacheRoot = new NmapRuntimePaths(missingZenmapBin).workerCacheRoot();

    assertTrue(Files.isDirectory(missingZenmapBin));
    assertEquals(
        missingZenmapBin.resolve("BurpPythonIDE").resolve("cpython-worker").normalize(),
        runtimeCacheRoot);
    assertTrue(Files.isDirectory(runtimeCacheRoot));
  }

  @Test
  void rejectsMissingPathThatDoesNotEndInZenmapBin() throws Exception {
    Path nmapRoot = Files.createDirectories(tempDir.resolve("Nmap"));
    Path invalidMissingPath = nmapRoot.resolve("not-zenmap/not-bin");

    IOException error =
        assertThrows(
            IOException.class, () -> new NmapRuntimePaths(invalidMissingPath).workerCacheRoot());

    assertTrue(error.getMessage().contains("must end in zenmap/bin"));
    assertTrue(
        error.getMessage().contains(invalidMissingPath.toAbsolutePath().normalize().toString()));
    assertTrue(Files.notExists(invalidMissingPath));
  }

  @Test
  void fixedUsesNormalizedFixedWindowsPath() throws Exception {
    assertEquals(
        NmapRuntimePaths.FIXED_ZENMAP_BIN.toAbsolutePath().normalize(),
        NmapRuntimePaths.fixed().zenmapBin());
  }

  @Test
  void rejectsMissingNmapInstallationRoot() {
    Path missingPath = tempDir.resolve("missing/zenmap/bin");
    IOException error =
        assertThrows(IOException.class, () -> new NmapRuntimePaths(missingPath).workerCacheRoot());

    assertTrue(error.getMessage().contains("Nmap"));
    assertTrue(
        error.getMessage().contains(missingPath.toAbsolutePath().normalize().toString()));
  }

  @Test
  void rejectsFileInsteadOfZenmapBinDirectory() throws Exception {
    Path file = Files.writeString(tempDir.resolve("zenmap-bin.txt"), "not-a-directory");

    IOException error =
        assertThrows(IOException.class, () -> new NmapRuntimePaths(file).workerCacheRoot());

    assertTrue(error.getMessage().contains("directory"));
    assertTrue(error.getMessage().contains(file.toAbsolutePath().normalize().toString()));
  }
}
