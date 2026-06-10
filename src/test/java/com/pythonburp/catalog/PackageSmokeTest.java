package com.pythonburp.catalog;

import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.python.GraalPyPythonRuntime;
import com.pythonburp.python.PythonRuntime;
import com.pythonburp.python.ScriptRunResult;
import com.pythonburp.python.ScriptStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class PackageSmokeTest {
    @Test
    void bundledSmokeTestsPass() throws Exception {
        PackageCatalog catalog = PackageCatalogLoader.loadBundled();
        StringBuilder failures = new StringBuilder();
        try (PythonRuntime runtime = new GraalPyPythonRuntime(new BurpBridge())) {
            for (PackageCatalogEntry entry : catalog.entries()) {
                ScriptRunResult result = runtime.execute(entry.smokeTest(), Duration.ofSeconds(30));
                if (result.status() != ScriptStatus.SUCCEEDED) {
                    failures.append(entry.name())
                        .append(" failed: ")
                        .append(result.errorMessage())
                        .append(System.lineSeparator());
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.toString());
    }
}
