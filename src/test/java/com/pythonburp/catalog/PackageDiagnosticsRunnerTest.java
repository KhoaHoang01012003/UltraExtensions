package com.pythonburp.catalog;

import com.pythonburp.python.PythonRuntime;
import com.pythonburp.python.ScriptRunResult;
import com.pythonburp.python.ScriptStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PackageDiagnosticsRunnerTest {
    @Test
    void runsSmokeTestsAndCapturesPassAndFailureDetails() {
        PackageCatalog catalog = new PackageCatalog(List.of(
            new PackageCatalogEntry("ok", "1", "pure-python", false, "import ok"),
            new PackageCatalogEntry("bad", "1", "native-candidate", true, "import bad")
        ));
        PackageDiagnosticsRunner runner = new PackageDiagnosticsRunner(
            () -> new StubRuntime("import bad")
        );

        List<PackageDiagnosticResult> results = runner.run(catalog, Duration.ofSeconds(1));

        assertEquals(PackageDiagnosticStatus.PASSED, results.get(0).status());
        assertEquals("stdout: import ok", results.get(0).stdout());
        assertEquals(PackageDiagnosticStatus.FAILED, results.get(1).status());
        assertTrue(results.get(1).errorMessage().contains("boom: import bad"));
    }

    private static final class StubRuntime implements PythonRuntime {
        private final String failingSource;

        private StubRuntime(String failingSource) {
            this.failingSource = failingSource;
        }

        @Override
        public ScriptRunResult execute(String source, Duration timeout) {
            if (source.equals(failingSource)) {
                return ScriptRunResult.failed("", "stderr: " + source, "boom: " + source);
            }
            return ScriptRunResult.succeeded("stdout: " + source, "");
        }

        @Override
        public void close() {
        }
    }
}
