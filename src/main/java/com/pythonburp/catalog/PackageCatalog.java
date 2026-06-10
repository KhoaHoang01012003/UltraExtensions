package com.pythonburp.catalog;

import java.util.List;
import java.util.Optional;

public record PackageCatalog(List<PackageCatalogEntry> entries) {
    public Optional<PackageCatalogEntry> find(String name) {
        return entries.stream()
            .filter(entry -> entry.name().equalsIgnoreCase(name))
            .findFirst();
    }
}
