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

  Path zenmapBin() {
    return zenmapBin;
  }

  Path workerCacheRoot() throws IOException {
    if (!Files.exists(zenmapBin)) {
      Path nmapInstallRoot = nmapInstallRoot();
      if (nmapInstallRoot == null || !Files.isDirectory(nmapInstallRoot)) {
        throw new IOException(
            "Nmap installation root is required at "
                + nmapInstallRoot
                + " before the CPython runtime can start; expected to create "
                + zenmapBin
                + ".");
      }
      Files.createDirectories(zenmapBin);
    }
    if (!Files.isDirectory(zenmapBin)) {
      throw new IOException("Expected Nmap installation tree to be a directory: " + zenmapBin);
    }

    Path cacheRoot = zenmapBin.resolve("BurpPythonIDE").resolve("cpython-worker").normalize();
    Files.createDirectories(cacheRoot);
    return cacheRoot;
  }

  private Path nmapInstallRoot() {
    Path binName = zenmapBin.getFileName();
    Path zenmapRoot = zenmapBin.getParent();
    Path zenmapName = zenmapRoot == null ? null : zenmapRoot.getFileName();
    if (binName == null
        || zenmapName == null
        || !"bin".equals(binName.toString())
        || !"zenmap".equals(zenmapName.toString())) {
      return null;
    }
    return zenmapRoot.getParent();
  }
}
