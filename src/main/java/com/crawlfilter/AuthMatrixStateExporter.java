package com.crawlfilter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public final class AuthMatrixStateExporter
{
    private static final String AUTH_MATRIX_VERSION = "0.8";
    private static final String DEFAULT_REGEX = "^HTTP/1\\.1 200 OK";

    private AuthMatrixStateExporter()
    {
    }

    public static String buildStateJson(List<RequestEntry> entries)
    {
        StringBuilder builder = new StringBuilder(4096);
        String encodedRegex = Base64.getEncoder().encodeToString(DEFAULT_REGEX.getBytes(StandardCharsets.UTF_8));

        builder.append("{\n");
        builder.append("  \"version\": ").append(jsonString(AUTH_MATRIX_VERSION)).append(",\n");
        builder.append("  \"arrayOfRoles\": [],\n");
        builder.append("  \"arrayOfUsers\": [],\n");
        builder.append("  \"arrayOfMessages\": [\n");

        for (int index = 0; index < entries.size(); index++)
        {
            RequestEntry entry = entries.get(index);
            String requestBase64 = Base64.getEncoder().encodeToString(entry.request().toByteArray().getBytes());

            builder.append("    {\n");
            builder.append("      \"index\": ").append(index).append(",\n");
            builder.append("      \"tableRow\": ").append(index).append(",\n");
            builder.append("      \"name\": ").append(jsonString(buildMessageName(entry))).append(",\n");
            builder.append("      \"roles\": {},\n");
            builder.append("      \"requestBase64\": ").append(jsonString(requestBase64)).append(",\n");
            builder.append("      \"protocol\": ").append(jsonString(entry.scheme())).append(",\n");
            builder.append("      \"port\": ").append(entry.port()).append(",\n");
            builder.append("      \"host\": ").append(jsonString(entry.host())).append(",\n");
            builder.append("      \"deleted\": false,\n");
            builder.append("      \"enabled\": true,\n");
            builder.append("      \"regexBase64\": ").append(jsonString(encodedRegex)).append(",\n");
            builder.append("      \"failureRegexMode\": false,\n");
            builder.append("      \"runResultForRoleID\": {},\n");
            builder.append("      \"runBase64ForUserID\": {}\n");
            builder.append("    }");

            if (index < entries.size() - 1)
            {
                builder.append(',');
            }
            builder.append('\n');
        }

        builder.append("  ],\n");
        builder.append("  \"arrayOfChains\": [],\n");
        builder.append("  \"arrayOfChainSources\": []\n");
        builder.append("}\n");

        return builder.toString();
    }

    private static String buildMessageName(RequestEntry entry)
    {
        return entry.index() + " " + entry.method() + " " + combinePathAndQuery(entry);
    }

    private static String combinePathAndQuery(RequestEntry entry)
    {
        if (entry.query() == null || entry.query().isBlank())
        {
            return entry.path();
        }

        return entry.path() + "?" + entry.query();
    }

    private static String jsonString(String value)
    {
        StringBuilder builder = new StringBuilder(value.length() + 16);
        builder.append('"');

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

        builder.append('"');
        return builder.toString();
    }
}
