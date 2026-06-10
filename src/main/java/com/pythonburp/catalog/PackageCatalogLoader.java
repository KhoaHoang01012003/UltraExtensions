package com.pythonburp.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PackageCatalogLoader {
    private static final String RESOURCE_PATH = "/package-catalog.json";

    private PackageCatalogLoader() {
    }

    public static PackageCatalog loadBundled() throws IOException {
        InputStream stream = PackageCatalogLoader.class.getResourceAsStream(RESOURCE_PATH);
        if (stream == null) {
            throw new IOException("Missing bundled package catalog resource " + RESOURCE_PATH);
        }
        try (stream) {
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    static PackageCatalog parse(String json) throws IOException {
        Objects.requireNonNull(json, "json");
        Cursor cursor = new Cursor(json);
        cursor.skipWhitespace();
        cursor.expect('[');

        List<PackageCatalogEntry> entries = new ArrayList<>();
        cursor.skipWhitespace();
        if (cursor.peek() != ']') {
            while (true) {
                entries.add(parseEntry(cursor));
                cursor.skipWhitespace();
                char next = cursor.peek();
                if (next == ',') {
                    cursor.advance();
                    continue;
                }
                if (next == ']') {
                    break;
                }
                throw cursor.error("Expected ',' or ']' after catalog entry");
            }
        }

        cursor.expect(']');
        cursor.skipWhitespace();
        cursor.ensureEnd();
        return new PackageCatalog(List.copyOf(entries));
    }

    private static PackageCatalogEntry parseEntry(Cursor cursor) throws IOException {
        cursor.skipWhitespace();
        cursor.expect('{');

        String name = null;
        String version = null;
        String tier = null;
        Boolean nativeRequired = null;
        String smokeTest = null;

        cursor.skipWhitespace();
        if (cursor.peek() != '}') {
            while (true) {
                cursor.skipWhitespace();
                String key = cursor.readString();
                cursor.skipWhitespace();
                cursor.expect(':');
                cursor.skipWhitespace();
                switch (key) {
                    case "name" -> name = cursor.readStringValue();
                    case "version" -> version = cursor.readStringValue();
                    case "tier" -> tier = cursor.readStringValue();
                    case "nativeRequired" -> nativeRequired = cursor.readBooleanValue("nativeRequired");
                    case "smokeTest" -> smokeTest = cursor.readStringValue();
                    default -> throw cursor.error("Unknown catalog field '" + key + "'");
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
                throw cursor.error("Expected ',' or '}' after catalog field");
            }
        }

        cursor.expect('}');

        if (name == null || version == null || tier == null || nativeRequired == null || smokeTest == null) {
            throw cursor.error("Catalog entry is missing one or more required fields");
        }

        return new PackageCatalogEntry(name, version, tier, nativeRequired, smokeTest);
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

        private boolean readBooleanValue(String fieldName) throws IOException {
            if (source.startsWith("true", index)) {
                index += 4;
                return true;
            }
            if (source.startsWith("false", index)) {
                index += 5;
                return false;
            }
            throw error("Field '" + fieldName + "' must be a boolean");
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
                throw error("Unexpected trailing content in catalog JSON");
            }
        }

        private IOException error(String message) {
            return new IOException(message + " at index " + index);
        }
    }
}
