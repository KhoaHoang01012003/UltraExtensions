package com.crawlfilter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public record TrackerConnectionConfig(
        String baseUrl,
        String roundId,
        String jwtToken
)
{
    public TrackerConnectionConfig
    {
        baseUrl = sanitize(baseUrl);
        roundId = sanitize(roundId);
        jwtToken = sanitize(jwtToken);
    }

    public static TrackerConnectionConfig normalize(String baseUrl, String roundId, String jwtToken)
    {
        String sanitizedBaseUrl = sanitize(baseUrl);
        if (!sanitizedBaseUrl.isBlank() && !sanitizedBaseUrl.contains("://"))
        {
            sanitizedBaseUrl = "https://" + sanitizedBaseUrl;
        }

        if (!sanitizedBaseUrl.isBlank())
        {
            try
            {
                URI uri = new URI(sanitizedBaseUrl);
                String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
                String host = uri.getHost();

                if (host == null || host.isBlank())
                {
                    throw new IllegalArgumentException("Tracker host is invalid.");
                }

                int port = uri.getPort();
                String normalizedBaseUrl = port >= 0
                        ? scheme + "://" + host.toLowerCase(Locale.ROOT) + ":" + port
                        : scheme + "://" + host.toLowerCase(Locale.ROOT);
                return new TrackerConnectionConfig(normalizedBaseUrl, roundId, jwtToken);
            }
            catch (URISyntaxException exception)
            {
                throw new IllegalArgumentException("Tracker host is invalid.", exception);
            }
        }

        return new TrackerConnectionConfig("", roundId, jwtToken);
    }

    public boolean isComplete()
    {
        return !baseUrl.isBlank() && !roundId.isBlank() && !jwtToken.isBlank();
    }

    public String referer()
    {
        return baseUrl + "/tracking/round/" + roundId;
    }

    private static String sanitize(String value)
    {
        return value == null ? "" : value.trim();
    }
}
