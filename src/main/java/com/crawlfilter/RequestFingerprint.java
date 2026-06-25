package com.crawlfilter;

import burp.api.montoya.http.HttpService;

import java.util.Locale;

public record RequestFingerprint(String scopeKey, String method, String path)
{
    public static RequestFingerprint from(HttpService service, String method, String path, boolean includeHost)
    {
        return new RequestFingerprint(
                includeHost ? formatServiceKey(service) : "",
                normalizeMethod(method),
                normalizePath(path)
        );
    }

    public static String normalizeMethod(String method)
    {
        if (method == null || method.isBlank())
        {
            return "UNKNOWN";
        }

        return method.trim().toUpperCase(Locale.ROOT);
    }

    public static String normalizePath(String path)
    {
        if (path == null || path.isBlank())
        {
            return "/";
        }

        return path.trim();
    }

    private static String formatServiceKey(HttpService service)
    {
        String scheme = service.secure() ? "https" : "http";
        return scheme + "://" + service.host().toLowerCase(Locale.ROOT) + ":" + service.port();
    }
}

