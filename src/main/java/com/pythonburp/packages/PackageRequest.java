package com.pythonburp.packages;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public record PackageRequest(String id, Type type, String value) {
    public PackageRequest {
        id = normalizeId(id);
        type = Objects.requireNonNull(type, "type");
        value = Objects.requireNonNull(value, "value");
    }

    public static PackageRequest pypi(String id, String requirement) {
        return new PackageRequest(id, Type.PYPI, requirement);
    }

    public static PackageRequest wheel(String id, Path wheel) {
        return new PackageRequest(id, Type.WHEEL, wheel.toAbsolutePath().normalize().toString());
    }

    public static PackageRequest requirements(String id, Path requirements) {
        return new PackageRequest(id, Type.REQUIREMENTS, requirements.toAbsolutePath().normalize().toString());
    }

    public Path path() { return Path.of(value); }

    public enum Type { PYPI, WHEEL, REQUIREMENTS }

    static String normalizeId(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
