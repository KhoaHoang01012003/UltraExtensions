package com.pythonburp.nativepack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NativePackManifestLoader {
    private static final String RESOURCE_PATH = "/native-pack-manifest.json";

    private NativePackManifestLoader() {
    }

    public static NativePackManifest loadBundled() throws IOException {
        InputStream stream = NativePackManifestLoader.class.getResourceAsStream(RESOURCE_PATH);
        if (stream == null) {
            throw new IOException("Missing bundled native pack manifest resource " + RESOURCE_PATH);
        }
        try (stream) {
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    static NativePackManifest parse(String json) throws IOException {
        Objects.requireNonNull(json, "json");
        Cursor cursor = new Cursor(json);
        cursor.skipWhitespace();
        cursor.expect('[');

        List<NativePackResource> resources = new ArrayList<>();
        cursor.skipWhitespace();
        if (cursor.peek() != ']') {
            while (true) {
                resources.add(parseResource(cursor));
                cursor.skipWhitespace();
                char next = cursor.peek();
                if (next == ',') {
                    cursor.advance();
                    continue;
                }
                if (next == ']') {
                    break;
                }
                throw cursor.error("Expected ',' or ']' after native pack resource");
            }
        }

        cursor.expect(']');
        cursor.skipWhitespace();
        cursor.ensureEnd();
        return new NativePackManifest(resources);
    }

    private static NativePackResource parseResource(Cursor cursor) throws IOException {
        cursor.skipWhitespace();
        cursor.expect('{');

        String packId = null;
        String os = null;
        String arch = null;
        String resourcePath = null;
        String targetPath = null;
        String sha256 = null;

        cursor.skipWhitespace();
        if (cursor.peek() != '}') {
            while (true) {
                cursor.skipWhitespace();
                String key = cursor.readString();
                cursor.skipWhitespace();
                cursor.expect(':');
                cursor.skipWhitespace();
                switch (key) {
                    case "packId" -> packId = cursor.readStringValue();
                    case "os" -> os = cursor.readStringValue();
                    case "arch" -> arch = cursor.readStringValue();
                    case "resourcePath" -> resourcePath = cursor.readStringValue();
                    case "targetPath" -> targetPath = cursor.readStringValue();
                    case "sha256" -> sha256 = cursor.readStringValue();
                    default -> throw cursor.error("Unknown native pack field '" + key + "'");
                }

                cursor.skipWhitespace();
                char next = cursor.peek();
                if (next == ',') {
                    cursor.advance();
                    continue;
                }
                if (next == '}') {
                    break;
                }
                throw cursor.error("Expected ',' or '}' after native pack field");
            }
        }

        cursor.expect('}');

        if (packId == null || os == null || arch == null || resourcePath == null || targetPath == null || sha256 == null) {
            throw cursor.error("Native pack resource is missing one or more required fields");
        }

        return new NativePackResource(packId, os, arch, resourcePath, targetPath, sha256);
    }

    private static final class Cursor {
        private final String source;
        private int index;

        private Cursor(String source) {
            this.source = source;
        }

        private void skipWhitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
        }

        private char peek() throws IOException {
            if (index >= source.length()) {
                throw error("Unexpected end of input");
            }
            return source.charAt(index);
        }

        private void advance() {
            index++;
        }

        private void expect(char expected) throws IOException {
            char actual = peek();
            if (actual != expected) {
                throw error("Expected '" + expected + "' but found '" + actual + "'");
            }
            advance();
        }

        private String readString() throws IOException {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (index < source.length()) {
                char ch = source.charAt(index++);
                if (ch == '"') {
                    return builder.toString();
                }
                if (ch == '\\') {
                    if (index >= source.length()) {
                        throw error("Unterminated escape sequence in string");
                    }
                    char escaped = source.charAt(index++);
                    switch (escaped) {
                        case '"', '\\', '/' -> builder.append(escaped);
                        case 'b' -> builder.append('\b');
                        case 'f' -> builder.append('\f');
                        case 'n' -> builder.append('\n');
                        case 'r' -> builder.append('\r');
                        case 't' -> builder.append('\t');
                        case 'u' -> builder.append(readUnicodeEscape());
                        default -> throw error("Invalid escape sequence '\\" + escaped + "'");
                    }
                    continue;
                }
                builder.append(ch);
            }
            throw error("Unterminated string literal");
        }

        private String readStringValue() throws IOException {
            return readString();
        }

        private char readUnicodeEscape() throws IOException {
            if (index + 4 > source.length()) {
                throw error("Incomplete unicode escape");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                char hex = source.charAt(index++);
                int digit = Character.digit(hex, 16);
                if (digit < 0) {
                    throw error("Invalid unicode escape digit '" + hex + "'");
                }
                value = (value << 4) | digit;
            }
            return (char) value;
        }

        private void ensureEnd() throws IOException {
            if (index != source.length()) {
                throw error("Unexpected trailing content in native pack manifest JSON");
            }
        }

        private IOException error(String message) {
            return new IOException(message + " at index " + index);
        }
    }
}
