package com.pythonburp.python;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pythonburp.storage.ExtensionDataPaths;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
