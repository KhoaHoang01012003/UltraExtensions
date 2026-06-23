package com.pythonburp.python;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

final class NmapRuntimePaths {
  static final Path FIXED_ZENMAP_BIN =
      Path.of("C:", "Program Files (x86)", "Nmap", "zenmap", "bin");

  private final Path zenmapBin;

  NmapRuntimePaths(Path zenmapBin) {
    this.zenmapBin = Objects.requireNonNull(zenmapBin, "zenmapBin").toAbsolutePath().normalize();
  }

  static NmapRuntimePaths fixed() {
    return new NmapRuntimePaths(FIXED_ZENMAP_BIN);
  }

  Path workerCacheRoot() throws IOException {
    if (!Files.exists(zenmapBin)) {
      throw new IOException(
          "Nmap is required at " + zenmapBin + " before the CPython runtime can start.");
    }
    if (!Files.isDirectory(zenmapBin)) {
      throw new IOException("Expected Nmap installation tree to be a directory: " + zenmapBin);
    }

    Path cacheRoot = zenmapBin.resolve("BurpPythonIDE").resolve("cpython-worker").normalize();
    Files.createDirectories(cacheRoot);
    return cacheRoot;
  }
}
