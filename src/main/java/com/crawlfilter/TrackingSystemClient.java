package com.crawlfilter;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class TrackingSystemClient
{
    private final MontoyaApi api;
    private final TrackerConnectionConfig config;
    private final Consumer<String> traceLogger;

    public TrackingSystemClient(MontoyaApi api, TrackerConnectionConfig config, Consumer<String> traceLogger)
    {
        this.api = api;
        this.config = config;
        this.traceLogger = traceLogger != null ? traceLogger : ignored -> { };
    }

    public List<TrackedApi> listApis() throws IOException
    {
        List<TrackedApi> apis = new ArrayList<>();
        int page = 1;

        while (true)
        {
            JsonObject response = sendJsonObject(
                    "GET",
                    "/api/tracking/round/" + encode(config.roundId()) + "/path/?page=" + page + "&page_size=2000&search=",
                    null,
                    200
            );

            JsonArray results = response.getAsJsonArray("results");
            if (results == null || results.isEmpty())
            {
                break;
            }

            for (JsonElement result : results)
            {
                JsonObject apiObject = result.getAsJsonObject();
                apis.add(new TrackedApi(
                        stringValue(apiObject, "id"),
                        stringValue(apiObject, "url"),
                        stringValue(apiObject, "name"),
                        stringValue(apiObject, "status")
                ));
            }

            JsonElement next = response.get("next");
            if (next == null || next.isJsonNull() || next.getAsString().isBlank())
            {
                break;
            }

            page++;
        }

        return apis;
    }

    public List<VulnerabilityTemplateOption> listVulnerabilityTemplates() throws IOException
    {
        JsonArray templates = sendJsonArray(
                "GET",
                "/api/tracking/round/" + encode(config.roundId()) + "/vulnerability/template/",
                null,
                200
        );

        List<VulnerabilityTemplateOption> options = new ArrayList<>(templates.size());
        for (JsonElement templateElement : templates)
        {
            JsonObject templateObject = templateElement.getAsJsonObject();
            options.add(new VulnerabilityTemplateOption(
                    stringValue(templateObject, "id"),
                    stringValue(templateObject, "name_text")
            ));
        }

        return options;
    }

    public List<ApiTestcaseOption> listApiTestcases(String requestId) throws IOException
    {
        JsonArray testcases = sendJsonArray(
                "GET",
                "/api/tracking/round/" + encode(config.roundId()) + "/path/" + encode(requestId) + "/testcase/",
                null,
                200
        );

        List<ApiTestcaseOption> options = new ArrayList<>(testcases.size());
        for (JsonElement testcaseElement : testcases)
        {
            JsonObject testcaseObject = testcaseElement.getAsJsonObject();
            options.add(new ApiTestcaseOption(
                    stringValue(testcaseObject, "id"),
                    stringValue(testcaseObject, "testcase_name"),
                    stringValue(testcaseObject, "testcase_status")
            ));
        }

        return options;
    }

    public void addTestcase(String requestId, String templateId) throws IOException
    {
        sendWithoutParsing(
                "POST",
                "/api/tracking/round/" + encode(config.roundId()) + "/path/" + encode(requestId) + "/testcase/",
                "{\"template_id\":\"" + escapeJson(templateId) + "\"}",
                201
        );
    }

    public void markTestcaseDone(String requestId, ApiTestcaseOption testcase) throws IOException
    {
        sendWithoutParsing(
                "PUT",
                "/api/tracking/round/" + encode(config.roundId()) + "/path/" + encode(requestId) + "/testcase/"
                        + encode(testcase.id()) + "/",
                "{\"id\":\"" + escapeJson(testcase.id()) + "\",\"testcase_name\":\"" + escapeJson(testcase.name())
                        + "\",\"testcase_status\":\"Done\"}",
                200
        );
    }

    public void markApiDone(String requestId) throws IOException
    {
        sendWithoutParsing(
                "PATCH",
                "/api/tracking/round/" + encode(config.roundId()) + "/path/" + encode(requestId) + "/",
                "{\"status\":\"Done\"}",
                200
        );
    }

    public void renameApi(String requestId, String name) throws IOException
    {
        sendWithoutParsing(
                "PATCH",
                "/api/tracking/round/" + encode(config.roundId()) + "/path/" + encode(requestId) + "/",
                "{\"name\":\"" + escapeJson(name) + "\"}",
                200
        );
    }

    private JsonObject sendJsonObject(String method, String path, String body, int expectedStatus) throws IOException
    {
        String responseBody = sendWithoutParsing(method, path, body, expectedStatus);
        return JsonParser.parseString(responseBody).getAsJsonObject();
    }

    private JsonArray sendJsonArray(String method, String path, String body, int expectedStatus) throws IOException
    {
        String responseBody = sendWithoutParsing(method, path, body, expectedStatus);
        return JsonParser.parseString(responseBody).getAsJsonArray();
    }

    private String sendWithoutParsing(String method, String path, String body, int expectedStatus) throws IOException
    {
        String fullUrl = config.baseUrl() + path;
        HttpRequest request = HttpRequest.httpRequestFromUrl(fullUrl)
                .withMethod(method)
                .withHeader("Authorization", "Bearer " + config.jwtToken())
                .withHeader("Accept", "application/json, text/plain, */*")
                .withHeader("Referer", config.referer());

        if (body != null)
        {
            request = request
                    .withHeader("Origin", config.baseUrl())
                    .withHeader("Content-Type", "application/json")
                    .withBody(body);
        }

        traceLogger.accept(buildRequestTrace(method, fullUrl, body));
        HttpRequestResponse response = api.http().sendRequest(request);
        if (!response.hasResponse())
        {
            traceLogger.accept("HTTP RESPONSE | <no response> | " + method + " " + fullUrl);
            throw new IOException("No response from tracking system.");
        }

        short statusCode = response.response().statusCode();
        String responseBody = response.response().bodyToString();
        traceLogger.accept(buildResponseTrace(method, fullUrl, statusCode, responseBody));
        if (statusCode != expectedStatus)
        {
            throw new IOException("Tracking system returned HTTP " + statusCode + " for " + method + " " + path
                    + " with body: " + abbreviate(responseBody));
        }

        return responseBody;
    }

    private static String stringValue(JsonObject object, String fieldName)
    {
        JsonElement value = object.get(fieldName);
        if (value == null || value.isJsonNull())
        {
            return "";
        }

        return value.getAsString();
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String abbreviate(String value)
    {
        if (value == null)
        {
            return "";
        }

        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= 240)
        {
            return normalized;
        }

        return normalized.substring(0, 237) + "...";
    }

    private static String escapeJson(String value)
    {
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++)
        {
            char current = value.charAt(index);
            switch (current)
            {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default ->
                {
                    if (current <= 0x1F)
                    {
                        builder.append(String.format("\\u%04x", (int) current));
                    }
                    else
                    {
                        builder.append(current);
                    }
                }
            }
        }

        return builder.toString();
    }

    private String buildRequestTrace(String method, String fullUrl, String body)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("HTTP REQUEST | ")
                .append(method)
                .append(' ')
                .append(fullUrl)
                .append(" | Headers: Authorization=Bearer ")
                .append(maskToken(config.jwtToken()))
                .append("; Accept=application/json, text/plain, */*; Referer=")
                .append(config.referer());

        if (body != null)
        {
            builder.append("; Origin=")
                    .append(config.baseUrl())
                    .append("; Content-Type=application/json; Body=")
                    .append(abbreviate(body));
        }

        return builder.toString();
    }

    private static String buildResponseTrace(String method, String fullUrl, short statusCode, String responseBody)
    {
        return "HTTP RESPONSE | " + statusCode + " | " + method + " " + fullUrl + " | Body=" + abbreviate(responseBody);
    }

    private static String maskToken(String token)
    {
        if (token == null || token.isBlank())
        {
            return "<empty>";
        }

        if (token.length() <= 12)
        {
            return token.charAt(0) + "***" + token.charAt(token.length() - 1);
        }

        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }

    public record TrackedApi(String id, String url, String name, String status)
    {
    }

    public record VulnerabilityTemplateOption(String id, String name)
    {
        @Override
        public String toString()
        {
            return name + " [" + id + "]";
        }
    }

    public record ApiTestcaseOption(String id, String name, String status)
    {
        @Override
        public String toString()
        {
            return name + " [" + status + "]";
        }
    }
}
