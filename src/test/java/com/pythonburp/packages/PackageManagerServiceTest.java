package com.pythonburp.packages;

import com.pythonburp.catalog.PackageCatalog;
import com.pythonburp.concurrency.RuntimeActivityCoordinator;
import com.pythonburp.storage.ExtensionDataCleaner;
import com.pythonburp.storage.ExtensionDataPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PackageManagerServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void installPersistsRequestAndBuildsEnvironment() throws Exception {
        ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("data"));
        PackageManagerService service = service(paths);

        PackageOperationResult result = service.installRequirement("demo-package==1.2.3", ignored -> {});

        assertTrue(result.succeeded(), result.message());
        assertEquals("demo-package==1.2.3", new PackageRequestStore(paths.packageRequests()).load().get(0).value());
        assertTrue(Files.exists(paths.userPackages().resolve("demo_package-1.2.3.dist-info/METADATA")));
    }

    private PackageManagerService service(ExtensionDataPaths paths) {
        EmbeddedPipRunner runner = new EmbeddedPipRunner() {
            @Override
            public PipRunResult run(List<String> command, java.util.function.Consumer<String> output) throws IOException {
                Path target = Path.of(command.get(command.indexOf("--target") + 1));
                Path dist = target.resolve("demo_package-1.2.3.dist-info");
                Files.createDirectories(dist);
                Files.writeString(dist.resolve("METADATA"), "Name: demo-package\nVersion: 1.2.3\n");
                return new PipRunResult(0, false, "ok", "");
            }
        };
        return new PackageManagerService(
            paths,
            new RuntimeActivityCoordinator(),
            new SharedPackageEnvironment(paths),
            new PackageRequestStore(paths.packageRequests()),
            new PackageSettingsStore(paths.settings().resolve("pip.properties")),
            new PackageInventoryReader(new PackageCatalog(List.of())),
            new ExtensionDataCleaner(paths),
            runner,
            tempDir.resolve("python.exe")
        );
    }
}
