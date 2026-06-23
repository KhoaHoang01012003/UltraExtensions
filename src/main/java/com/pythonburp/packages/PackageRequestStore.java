package com.pythonburp.packages;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class PackageRequestStore {
    private final Path path;

    public PackageRequestStore(Path path) {
        this.path = path.toAbsolutePath().normalize();
    }

    public List<PackageRequest> load() throws IOException {
        if (!Files.exists(path)) return List.of();
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) { values.load(input); }
        List<PackageRequest> requests = new ArrayList<>();
        int count = Integer.parseInt(values.getProperty("count", "0"));
        for (int index = 0; index < count; index++) {
            String prefix = index + ".";
            String id = values.getProperty(prefix + "id", "");
            String type = values.getProperty(prefix + "type", "");
            String value = values.getProperty(prefix + "value", "");
            if (!id.isBlank() && !type.isBlank()) {
                requests.add(new PackageRequest(id, PackageRequest.Type.valueOf(type), value));
            }
        }
        return List.copyOf(requests);
    }

    public void save(List<PackageRequest> requests) throws IOException {
        Files.createDirectories(path.getParent());
        Properties values = new Properties();
        values.setProperty("count", Integer.toString(requests.size()));
        for (int index = 0; index < requests.size(); index++) {
            PackageRequest request = requests.get(index);
            String prefix = index + ".";
            values.setProperty(prefix + "id", request.id());
            values.setProperty(prefix + "type", request.type().name());
            values.setProperty(prefix + "value", request.value());
        }
        Path staging = Files.createTempFile(path.getParent(), "requests-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(staging)) {
                values.store(output, "Burp Python IDE user package requests");
            }
            Files.move(staging, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staging);
        }
    }
}
