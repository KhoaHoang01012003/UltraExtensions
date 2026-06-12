package com.pythonburp.packages;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PackageManagerSettingsTest {
    @Test
    void redactsProxyCredentials() {
        PackageManagerSettings settings = new PackageManagerSettings(
            "https://pypi.org/simple", "", "http://user:secret@proxy:8080", List.of(), 30
        );

        assertFalse(settings.sanitizedSummary().contains("secret"));
        assertTrue(settings.sanitizedSummary().contains("proxy:8080"));
    }

    @Test
    void validatesTimeout() {
        assertThrows(IllegalArgumentException.class,
            () -> new PackageManagerSettings("", "", "", List.of(), 0));
    }
}
