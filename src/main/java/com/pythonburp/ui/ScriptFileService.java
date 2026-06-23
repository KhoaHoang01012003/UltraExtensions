package com.pythonburp.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ScriptFileService {
    private ScriptFileService() {
    }

    public static String load(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    public static void save(Path path, String source) throws IOException {
        Files.writeString(path, source, StandardCharsets.UTF_8);
    }
}
