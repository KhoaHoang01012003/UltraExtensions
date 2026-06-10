package com.pythonburp.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PackageCatalogLoader {
    private static final Pattern OBJECT = Pattern.compile("\\{([^}]*)}");

    private PackageCatalogLoader() {
    }

    public static PackageCatalog loadBundled() throws IOException {
        try (InputStream stream = PackageCatalogLoader.class.getResourceAsStream("/package-catalog.json")) {
            if (stream == null) {
                throw new IOException("Missing /package-catalog.json");
            }
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    static PackageCatalog parse(String json) {
        List<PackageCatalogEntry> entries = new ArrayList<>();
        Matcher matcher = OBJECT.matcher(json);
        while (matcher.find()) {
            String object = matcher.group(1);
            entries.add(new PackageCatalogEntry(
                stringValue(object, "name"),
                stringValue(object, "version"),
                stringValue(object, "tier"),
                booleanValue(object, "nativeRequired"),
                stringValue(object, "smokeTest")
            ));
        }
        return new PackageCatalog(List.copyOf(entries));
    }

    private static String stringValue(String object, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(object);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing string key: " + key);
        }
        return matcher.group(1);
    }

    private static boolean booleanValue(String object, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)");
        Matcher matcher = pattern.matcher(object);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing boolean key: " + key);
        }
        return Boolean.parseBoolean(matcher.group(1));
    }
}
