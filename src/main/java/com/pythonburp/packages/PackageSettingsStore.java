package com.pythonburp.packages;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public final class PackageSettingsStore {
    private final Path path;

    public PackageSettingsStore(Path path) {
        this.path = path.toAbsolutePath().normalize();
    }

    public PackageManagerSettings load() throws IOException {
        if (!Files.exists(path)) return PackageManagerSettings.defaults();
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
        }
        String hosts = values.getProperty("trustedHosts", "");
        List<String> trusted = hosts.isBlank() ? List.of() : Arrays.stream(hosts.split(","))
            .map(String::trim).filter(value -> !value.isBlank()).toList();
        return new PackageManagerSettings(
            values.getProperty("indexUrl", ""),
            values.getProperty("extraIndexUrl", ""),
            values.getProperty("proxyUrl", ""),
            trusted,
            Integer.parseInt(values.getProperty("timeoutSeconds", "60"))
        );
    }

    public void save(PackageManagerSettings settings) throws IOException {
        Files.createDirectories(path.getParent());
        Properties values = new Properties();
        values.setProperty("indexUrl", settings.indexUrl());
        values.setProperty("extraIndexUrl", settings.extraIndexUrl());
        values.setProperty("proxyUrl", settings.proxyUrl());
        values.setProperty("trustedHosts", String.join(",", settings.trustedHosts()));
        values.setProperty("timeoutSeconds", Integer.toString(settings.timeoutSeconds()));
        Path staging = Files.createTempFile(path.getParent(), "pip-settings-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(staging)) {
                values.store(output, "Burp Python IDE package settings");
            }
            try {
                Files.move(staging, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(staging, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staging);
        }
    }
}
