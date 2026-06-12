package com.pythonburp.packages;

public record PackageInventoryEntry(
    String name,
    String activeVersion,
    String source,
    String bundledFallback,
    boolean nativeFiles
) {
}
