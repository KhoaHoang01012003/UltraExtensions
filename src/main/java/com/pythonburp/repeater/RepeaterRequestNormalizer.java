package com.pythonburp.repeater;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class RepeaterRequestNormalizer {
    private RepeaterRequestNormalizer() {
    }

    public static Optional<String> normalizeGetToPost(String requestText) {
        ParsedRequest parsed = ParsedRequest.parse(requestText);
        if (!"GET".equalsIgnoreCase(parsed.method())) {
            return Optional.empty();
        }
        if (parsed.query().isBlank()) {
            return Optional.empty();
        }
        if (!looksLikeFormParameters(parsed.query())) {
            return Optional.empty();
        }
        return Optional.of(parsed.withMethod("POST").withBody(parsed.query()).render());
    }

    static boolean looksLikeFormParameters(String text) {
        String trimmed = text == null ? "" : text.trim();
        return !trimmed.isEmpty() && trimmed.contains("=") && !trimmed.startsWith("{") && !trimmed.startsWith("[");
    }

    record ParsedRequest(String method, String target, String httpVersion, List<String> headers,
                         String body, String query) {
        static ParsedRequest parse(String requestText) {
            Objects.requireNonNull(requestText, "requestText");
            String normalized = requestText.replace("\r\n", "\n").replace('\r', '\n');
            int separator = normalized.indexOf("\n\n");
            String headerPart = separator >= 0 ? normalized.substring(0, separator) : normalized;
            String bodyPart = separator >= 0 ? normalized.substring(separator + 2) : "";
            String[] headerLines = headerPart.split("\n", -1);
            if (headerLines.length == 0 || headerLines[0].isBlank()) {
                throw new IllegalArgumentException("Request does not contain a request line");
            }

            String[] requestLineParts = headerLines[0].trim().split("\\s+", 3);
            if (requestLineParts.length < 2) {
                throw new IllegalArgumentException("Request line is malformed: " + headerLines[0]);
            }
            String method = requestLineParts[0];
            String target = requestLineParts[1];
            String version = requestLineParts.length >= 3 ? requestLineParts[2] : "HTTP/1.1";
            String query = extractQuery(target);
            List<String> headers = new ArrayList<>();
            for (int i = 1; i < headerLines.length; i++) {
                if (!headerLines[i].isBlank()) {
                    headers.add(headerLines[i]);
                }
            }
            return new ParsedRequest(method, target, version, headers, bodyPart, query);
        }

        ParsedRequest withMethod(String newMethod) {
            String newTarget = stripQuery(target);
            return new ParsedRequest(newMethod, newTarget, httpVersion, headers, body, query);
        }

        ParsedRequest withBody(String newBody) {
            return new ParsedRequest(method, target, httpVersion, headers, newBody, query);
        }

        String render() {
            String path = stripQuery(target);
            String bodyText = body == null ? "" : body;
            List<String> outputHeaders = new ArrayList<>();
            boolean sawContentType = false;
            boolean sawContentLength = false;
            for (String header : headers) {
                int colon = header.indexOf(':');
                if (colon <= 0) {
                    outputHeaders.add(header);
                    continue;
                }
                String name = header.substring(0, colon).trim();
                String value = header.substring(colon + 1).trim();
                if ("Content-Length".equalsIgnoreCase(name)) {
                    sawContentLength = true;
                    continue;
                }
                if ("Content-Type".equalsIgnoreCase(name)) {
                    sawContentType = true;
                }
                outputHeaders.add(name + ": " + value);
            }
            if ("POST".equalsIgnoreCase(method) && !sawContentType && !bodyText.isBlank()) {
                outputHeaders.add("Content-Type: application/x-www-form-urlencoded");
            }
            if ("POST".equalsIgnoreCase(method)) {
                outputHeaders.add("Content-Length: " + bodyText.getBytes(StandardCharsets.UTF_8).length);
            } else if (sawContentLength && !bodyText.isBlank()) {
                outputHeaders.add("Content-Length: " + bodyText.getBytes(StandardCharsets.UTF_8).length);
            }
            StringBuilder builder = new StringBuilder();
            builder.append(method).append(' ').append(path).append(' ').append(httpVersion);
            for (String header : outputHeaders) {
                builder.append("\r\n").append(header);
            }
            builder.append("\r\n\r\n");
            if (!bodyText.isEmpty()) {
                builder.append(bodyText);
            }
            return builder.toString();
        }

        private static String extractQuery(String target) {
            try {
                URI uri = new URI(target);
                if (uri.getRawQuery() != null) {
                    return uri.getRawQuery();
                }
            } catch (URISyntaxException ignored) {
                // Fall back to simple parsing.
            }
            int question = target.indexOf('?');
            return question >= 0 ? target.substring(question + 1) : "";
        }

        private static String stripQuery(String target) {
            try {
                URI uri = new URI(target);
                if (uri.getRawQuery() == null) {
                    return target;
                }
                if (uri.getScheme() != null && uri.getHost() != null) {
                    StringBuilder builder = new StringBuilder();
                    builder.append(uri.getScheme()).append("://").append(uri.getAuthority());
                    String path = uri.getRawPath();
                    if (path != null) {
                        builder.append(path);
                    }
                    return builder.toString();
                }
            } catch (URISyntaxException ignored) {
                // Fall back to simple parsing.
            }
            int question = target.indexOf('?');
            return question >= 0 ? target.substring(0, question) : target;
        }
    }
}
