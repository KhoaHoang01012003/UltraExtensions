package com.pythonburp.packages;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PackageRequestStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTripsRequests() throws Exception {
        PackageRequestStore store = new PackageRequestStore(tempDir.resolve("requests.properties"));
        List<PackageRequest> expected = List.of(
            PackageRequest.pypi("requests", "requests==2.34.2"),
            PackageRequest.wheel("demo", tempDir.resolve("demo.whl"))
        );

        store.save(expected);

        assertEquals(expected, store.load());
    }
}
