package com.pythonburp.packages;

import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.catalog.PackageCatalog;
import com.pythonburp.concurrency.RuntimeActivityCoordinator;
import com.pythonburp.python.CPythonRuntimeFactory;
import com.pythonburp.python.PythonRuntime;
import com.pythonburp.python.ScriptRunResult;
import com.pythonburp.python.ScriptStatus;
import com.pythonburp.storage.ExtensionDataCleaner;
import com.pythonburp.storage.ExtensionDataPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PackageManagerIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void installsLocalWheelAndImportsItFromNewWorker() throws Exception {
        ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("data"));
        CPythonRuntimeFactory runtimeFactory = new CPythonRuntimeFactory(paths);
        RuntimeActivityCoordinator coordinator = new RuntimeActivityCoordinator();
        PackageManagerService service = new PackageManagerService(
            paths, coordinator, new SharedPackageEnvironment(paths),
            new PackageRequestStore(paths.packageRequests()),
            new PackageSettingsStore(paths.settings().resolve("pip.properties")),
            new PackageInventoryReader(new PackageCatalog(List.of())),
            new ExtensionDataCleaner(paths), new EmbeddedPipRunner(), runtimeFactory::pythonExecutable
        );
        Path wheel = createWheel(tempDir.resolve("demo_package-1.0.0-py3-none-any.whl"));

        List<String> pipOutput = new CopyOnWriteArrayList<>();
        PackageOperationResult installed = service.installWheel(wheel, pipOutput::add);

        assertTrue(installed.succeeded(), installed.message() + System.lineSeparator() + String.join(System.lineSeparator(), pipOutput));
        try (PythonRuntime runtime = runtimeFactory.get(new BurpBridge())) {
            ScriptRunResult result = runtime.execute(
                "import demo_package; print(demo_package.VALUE)", Duration.ofSeconds(30));
            assertEquals(ScriptStatus.SUCCEEDED, result.status(), result.stderr() + result.errorMessage());
            assertTrue(result.stdout().contains("installed-from-wheel"));
        }
    }

    private static Path createWheel(Path wheel) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(wheel))) {
            entry(zip, "demo_package/__init__.py", "VALUE = 'installed-from-wheel'\n");
            entry(zip, "demo_package-1.0.0.dist-info/METADATA",
                "Metadata-Version: 2.1\nName: demo-package\nVersion: 1.0.0\n");
            entry(zip, "demo_package-1.0.0.dist-info/WHEEL",
                "Wheel-Version: 1.0\nGenerator: BurpPythonIDE\nRoot-Is-Purelib: true\nTag: py3-none-any\n");
            entry(zip, "demo_package-1.0.0.dist-info/RECORD",
                "demo_package/__init__.py,,\ndemo_package-1.0.0.dist-info/METADATA,,\n" +
                    "demo_package-1.0.0.dist-info/WHEEL,,\ndemo_package-1.0.0.dist-info/RECORD,,\n");
        }
        return wheel;
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
