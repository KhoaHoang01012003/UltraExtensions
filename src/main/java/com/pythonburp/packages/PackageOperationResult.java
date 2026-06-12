package com.pythonburp.packages;

import java.util.List;

public record PackageOperationResult(boolean succeeded, String message, List<PackageInventoryEntry> inventory) {
    public PackageOperationResult {
        inventory = List.copyOf(inventory == null ? List.of() : inventory);
    }
}
