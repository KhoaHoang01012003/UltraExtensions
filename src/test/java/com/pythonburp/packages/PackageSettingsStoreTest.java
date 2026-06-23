package com.pythonburp.packages;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PackageSettingsStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTripsSettings() throws Exception {
        PackageSettingsStore store = new PackageSettingsStore(tempDir.resolve("pip.properties"));
        PackageManagerSettings expected = new PackageManagerSettings(
            "https://pypi.org/simple", "https://mirror/simple", "http://proxy:8080",
            List.of("mirror"), 60
        );

        store.save(expected);

        assertEquals(expected, store.load());
    }
}
