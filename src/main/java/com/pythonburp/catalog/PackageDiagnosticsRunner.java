package com.pythonburp.catalog;

import com.pythonburp.python.PythonRuntime;
import com.pythonburp.python.ScriptRunResult;
import com.pythonburp.python.ScriptStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class PackageDiagnosticsRunner {
    private final Supplier<PythonRuntime> runtimeFactory;

    public PackageDiagnosticsRunner(Supplier<PythonRuntime> runtimeFactory) {
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
    }

    public List<PackageDiagnosticResult> run(PackageCatalog catalog, Duration timeoutPerPackage) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(timeoutPerPackage, "timeoutPerPackage");

        List<PackageDiagnosticResult> results = new ArrayList<>();
        try (PythonRuntime runtime = runtimeFactory.get()) {
            for (PackageCatalogEntry entry : catalog.entries()) {
                ScriptRunResult result = runtime.execute(entry.smokeTest(), timeoutPerPackage);
                PackageDiagnosticStatus status = result.status() == ScriptStatus.SUCCEEDED
                    ? PackageDiagnosticStatus.PASSED
                    : PackageDiagnosticStatus.FAILED;
                results.add(new PackageDiagnosticResult(
                    entry,
                    status,
                    result.stdout(),
                    result.stderr(),
                    result.errorMessage()
                ));
            }
        }
        return List.copyOf(results);
    }
}
