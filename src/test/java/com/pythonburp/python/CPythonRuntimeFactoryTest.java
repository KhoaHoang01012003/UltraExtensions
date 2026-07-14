package com.pythonburp.python;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pythonburp.storage.ExtensionDataPaths;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CPythonRuntimeFactoryTest {
  @TempDir Path tempDir;

  @Test
  void exposesInjectedPythonExecutableAndInterpreterAwarePackageRoot() throws Exception {
    Path fakePython = Files.writeString(tempDir.resolve("python.exe"), "fake");
    Path helperRoot = Files.createDirectories(tempDir.resolve("helper-root"));
    ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("localappdata/BurpPythonIDE"));
    CPythonRuntimeFactory factory = new CPythonRuntimeFactory(
        new PythonRuntimeEnvironment(fakePython, 3, 14, 0, "Windows", "AMD64", true),
        paths,
        () -> helperRoot
    );

    Path pythonExecutable = factory.pythonExecutable();

    assertEquals("python.exe", pythonExecutable.getFileName().toString());
    assertEquals(fakePython.toAbsolutePath().normalize(), pythonExecutable);
    assertEquals(
        paths.root().resolve("packages/python-3.14-windows-x64"),
        factory.userPackages());
  }

  @Test
  void rejectsNonPython3Interpreter() throws Exception {
    Path fakePython = Files.writeString(tempDir.resolve("python.exe"), "fake");
    ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("localappdata/BurpPythonIDE"));
    PythonRuntimeEnvironment environment =
        new PythonRuntimeEnvironment(fakePython, 2, 7, 18, "Windows", "AMD64", true);

    IllegalStateException error = assertThrows(IllegalStateException.class, () ->
        new CPythonRuntimeFactory(environment, paths, tempHelperSupplier()));

    assertTrue(error.getMessage().contains("Python 3"));
  }

  @Test
  void acceptsPython3InterpreterEvenWhenPipIsUnavailable() throws Exception {
    Path fakePython = Files.writeString(tempDir.resolve("python.exe"), "fake");
    Path helperRoot = Files.createDirectories(tempDir.resolve("helper-root"));
    ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("localappdata/BurpPythonIDE"));

    CPythonRuntimeFactory factory = new CPythonRuntimeFactory(
        new PythonRuntimeEnvironment(fakePython, 3, 14, 0, "Windows", "AMD64", false),
        paths,
        () -> helperRoot
    );

    assertEquals(fakePython.toAbsolutePath().normalize(), factory.pythonExecutable());
    assertTrue(!factory.environment().pipAvailable());
  }

  @Test
  void keepsBundledFallbackEnabledEvenWhenStartupProbeReportedWarning() throws Exception {
    Path fakePython = Files.writeString(tempDir.resolve("python.exe"), "fake");
    Path helperRoot = Files.createDirectories(tempDir.resolve("helper-root"));
    Path pipRoot = Files.createDirectories(tempDir.resolve("pip-root"));
    Path stdlibRoot = Files.createDirectories(tempDir.resolve("stdlib-root"));
    Path compatRoot = Files.createDirectories(tempDir.resolve("compat-root"));
    ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("localappdata/BurpPythonIDE"));
    PythonRuntimeEnvironment environment =
        new PythonRuntimeEnvironment(fakePython, 3, 14, 0, "Windows", "AMD64", false);

    CPythonRuntimeFactory factory = reflectiveFactory(
        new NmapRuntimePaths(tempDir.resolve("Nmap/zenmap/bin")),
        paths,
        environment,
        true,
        pipRoot,
        stdlibRoot,
        compatRoot,
        "Bundled pip startup probe failed: exit code 1",
        () -> helperRoot
    );

    assertTrue(factory.pipAvailable());
    assertTrue(factory.usingBundledPipFallback());
    assertTrue(factory.pipProbeWarning().contains("Bundled pip startup probe failed"));
  }

  @Test
  void exportsCompleteCompatibilityRuntimeToPipEnvironment() throws Exception {
    Path fakePython = Files.writeString(tempDir.resolve("python.exe"), "fake");
    Path helperRoot = Files.createDirectories(tempDir.resolve("helper-root"));
    Path pipRoot = Files.createDirectories(tempDir.resolve("pip-root"));
    Path compatibilityRoot = Files.createDirectories(tempDir.resolve("python-compat-3.14.3"));
    Files.writeString(compatibilityRoot.resolve("python314.zip"), "zip");
    ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("localappdata/BurpPythonIDE"));
    PythonRuntimeEnvironment environment =
        new PythonRuntimeEnvironment(fakePython, 3, 14, 3, "Windows", "AMD64", false);

    CPythonRuntimeFactory factory = reflectiveFactory(
        new NmapRuntimePaths(tempDir.resolve("Nmap/zenmap/bin")),
        paths,
        environment,
        true,
        pipRoot,
        compatibilityRoot.resolve("python314.zip"),
        compatibilityRoot,
        null,
        () -> helperRoot
    );

    Map<String, String> environmentOverrides = factory.pipEnvironmentOverrides();
    assertEquals(compatibilityRoot.toString(), environmentOverrides.get("BURP_PYTHON_COMPAT_ROOT"));
    assertTrue(environmentOverrides.get("PYTHONPATH").startsWith(
        compatibilityRoot.resolve("python314.zip") + java.io.File.pathSeparator + compatibilityRoot));
    assertFalse(environmentOverrides.containsKey("BURP_PYTHON_FALLBACK_STDLIB_ROOT"));
    assertFalse(environmentOverrides.containsKey("BURP_PYTHON_COMPAT_NATIVE_ROOT"));
  }

  @Test
  void wrapsMissingZenmapPythonWithActionableMessageForPythonExecutable() {
    ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("localappdata/BurpPythonIDE"));
    IllegalStateException error =
        assertThrows(IllegalStateException.class,
            () -> new CPythonRuntimeFactory(new NmapRuntimePaths(tempDir.resolve("missing/zenmap/bin")), paths));

    assertTrue(error.getMessage().contains("Failed to probe Zenmap Python"));
    assertTrue(error.getCause().getMessage().contains("Zenmap"));
  }

  @Test
  void rejectsNullConstructorDependencies() {
    ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("localappdata/BurpPythonIDE"));
    NmapRuntimePaths runtimePaths =
        new NmapRuntimePaths(tempDir.resolve("Nmap/zenmap/bin"));
    Path fakePython = tempDir.resolve("python.exe");
    PythonRuntimeEnvironment environment =
        new PythonRuntimeEnvironment(fakePython, 3, 14, 0, "Windows", "AMD64", true);

    assertThrows(NullPointerException.class, () -> new CPythonRuntimeFactory((Path) null, paths));
    assertThrows(NullPointerException.class, () -> new CPythonRuntimeFactory((NmapRuntimePaths) null, paths));
    assertThrows(NullPointerException.class, () -> new CPythonRuntimeFactory(runtimePaths, null));
    assertThrows(NullPointerException.class, () -> new CPythonRuntimeFactory((PythonRuntimeEnvironment) null, paths));
    assertThrows(NullPointerException.class, () -> new CPythonRuntimeFactory(environment, null));
  }

  private Supplier<Path> tempHelperSupplier() {
    return () -> tempDir.resolve("helper-root");
  }

  @SuppressWarnings("unchecked")
  private static CPythonRuntimeFactory reflectiveFactory(
      NmapRuntimePaths runtimePaths,
      ExtensionDataPaths paths,
      PythonRuntimeEnvironment environment,
      boolean pipAvailable,
      Path pipBootstrapRoot,
      Path stdlibFallbackRoot,
      Path compatNativeRoot,
      String pipProbeWarning,
      Supplier<Path> helperRootSupplier) throws Exception {
    Constructor<CPythonRuntimeFactory> constructor =
        (Constructor<CPythonRuntimeFactory>) CPythonRuntimeFactory.class.getDeclaredConstructor(
            NmapRuntimePaths.class,
            ExtensionDataPaths.class,
            PythonRuntimeEnvironment.class,
            boolean.class,
            Path.class,
            Path.class,
            Path.class,
            String.class,
            Supplier.class
        );
    constructor.setAccessible(true);
    return constructor.newInstance(
        runtimePaths,
        paths,
        environment,
        pipAvailable,
        pipBootstrapRoot,
        stdlibFallbackRoot,
        compatNativeRoot,
        pipProbeWarning,
        helperRootSupplier
    );
  }
}
