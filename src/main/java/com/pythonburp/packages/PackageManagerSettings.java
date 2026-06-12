package com.pythonburp.packages;

import java.net.URI;
import java.util.List;

public record PackageManagerSettings(
    String indexUrl,
    String extraIndexUrl,
    String proxyUrl,
    List<String> trustedHosts,
    int timeoutSeconds
) {
    public PackageManagerSettings {
        indexUrl = normalize(indexUrl);
        extraIndexUrl = normalize(extraIndexUrl);
        proxyUrl = normalize(proxyUrl);
        trustedHosts = List.copyOf(trustedHosts == null ? List.of() : trustedHosts);
        if (timeoutSeconds < 1 || timeoutSeconds > 600) {
            throw new IllegalArgumentException("timeoutSeconds must be between 1 and 600");
        }
    }

    public static PackageManagerSettings defaults() {
        return new PackageManagerSettings("", "", "", List.of(), 60);
    }

    public String sanitizedSummary() {
        return "index=" + indexUrl + ", extraIndex=" + extraIndexUrl + ", proxy=" + redactProxy(proxyUrl);
    }

    private static String redactProxy(String proxy) {
        if (proxy.isBlank()) return "";
        try {
            URI uri = URI.create(proxy);
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toString();
        } catch (Exception ignored) {
            int at = proxy.lastIndexOf('@');
            return at >= 0 ? proxy.substring(at + 1) : proxy;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
