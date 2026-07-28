package com.pythonburp.catalog;

import java.util.Objects;

public record PackageDiagnosticResult(
    PackageCatalogEntry entry,
    PackageDiagnosticStatus status,
    String stdout,
    String stderr,
    String errorMessage
) {
    public PackageDiagnosticResult {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");
        Objects.requireNonNull(errorMessage, "errorMessage");
    }

    public static PackageDiagnosticResult notRun(PackageCatalogEntry entry) {
        return new PackageDiagnosticResult(entry, PackageDiagnosticStatus.NOT_RUN, "", "", "");
    }
}
