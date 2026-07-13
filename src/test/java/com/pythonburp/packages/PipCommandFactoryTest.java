package com.pythonburp.packages;

import com.pythonburp.storage.ExtensionDataPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PipCommandFactoryTest {
    @TempDir
    Path tempDir;

    @Test
    void requirementAndPathsRemainSeparateArguments() {
        ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("data"));
        PipCommandFactory factory = new PipCommandFactory(tempDir.resolve("python.exe"), paths);
        PackageManagerSettings settings = PackageManagerSettings.defaults();

        List<String> command = factory.installRequirement("requests>=2.34,<3", paths.userPackages(), settings);

        assertTrue(command.contains("requests>=2.34,<3"));
        assertFalse(command.contains("cmd.exe"));
        assertFalse(command.contains("powershell.exe"));
        assertTrue(command.contains("-c"));
        assertFalse(command.contains("-m"));
        assertTrue(command.contains(paths.userPackages().toString()));
    }
}
