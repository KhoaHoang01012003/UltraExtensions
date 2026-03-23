package com.crawlfilter;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.proxy.http.InterceptedRequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record RequestEntry(
        long index,
        String firstSeenAt,
        String method,
        String scheme,
        String host,
        int port,
        String path,
        String query,
        String url,
        HttpRequest request
)
{
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static RequestEntry from(long index, InterceptedRequest interceptedRequest, String method, String path)
    {
        HttpService service = interceptedRequest.httpService();
        String scheme = service.secure() ? "https" : "http";
        String host = service.host();
        int port = service.port();
        String query = safeQuery(interceptedRequest);
        String url;

        try
        {
            url = interceptedRequest.url();
        }
        catch (RuntimeException ignored)
        {
            url = host + path;
        }

        return new RequestEntry(
                index,
                TIMESTAMP_FORMATTER.format(LocalDateTime.now()),
                method,
                scheme,
                host,
                port,
                path,
                query,
                url,
                interceptedRequest.copyToTempFile()
        );
    }

    public static RequestEntry from(long index, HttpRequest request, String firstSeenAt)
    {
        HttpService service = request.httpService();
        String scheme = service.secure() ? "https" : "http";
        String host = service.host();
        int port = service.port();
        String method = safeMethod(request);
        String path = safePath(request);
        String query = safeQuery(request);
        String url = safeUrl(request, scheme, host, port, path, query);

        return new RequestEntry(
                index,
                firstSeenAt,
                method,
                scheme,
                host,
                port,
                path,
                query,
                url,
                request.copyToTempFile()
        );
    }

    private static String safeQuery(InterceptedRequest interceptedRequest)
    {
        try
        {
            return interceptedRequest.query();
        }
        catch (RuntimeException ignored)
        {
            return "";
        }
    }

    private static String safeMethod(HttpRequest request)
    {
        try
        {
            return RequestFingerprint.normalizeMethod(request.method());
        }
        catch (RuntimeException ignored)
        {
            return "UNKNOWN";
        }
    }

    private static String safePath(HttpRequest request)
    {
        try
        {
            return RequestFingerprint.normalizePath(request.pathWithoutQuery());
        }
        catch (RuntimeException ignored)
        {
            return "/";
        }
    }

    private static String safeQuery(HttpRequest request)
    {
        try
        {
            return request.query();
        }
        catch (RuntimeException ignored)
        {
            return "";
        }
    }

    private static String safeUrl(HttpRequest request, String scheme, String host, int port, String path, String query)
    {
        try
        {
            return request.url();
        }
        catch (RuntimeException ignored)
        {
            StringBuilder builder = new StringBuilder();
            builder.append(scheme)
                    .append("://")
                    .append(host)
                    .append(':')
                    .append(port)
                    .append(path);

            if (!query.isBlank())
            {
                builder.append('?').append(query);
            }

            return builder.toString();
        }
    }
}
