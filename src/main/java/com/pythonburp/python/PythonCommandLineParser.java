package com.pythonburp.python;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class PythonCommandLineParser {
    private PythonCommandLineParser() {
    }

    public static List<String> parseTail(String tail) throws IOException {
        String value = tail == null ? "" : tail.trim();
        if (value.isBlank()) {
            throw new IOException("Custom command is empty. Enter the arguments after python.exe.");
        }

        List<String> tokens = tokenize(value);
        if (tokens.isEmpty()) {
            throw new IOException("Custom command is empty. Enter the arguments after python.exe.");
        }
        String first = tokens.get(0).toLowerCase(java.util.Locale.ROOT);
        if (first.endsWith("python") || first.endsWith("python.exe") || "py".equals(first) || "py.exe".equals(first)) {
            throw new IOException("Enter only the command tail after python.exe, for example: -m abc -h xyz");
        }
        if ("-i".equals(first)) {
            throw new IOException("Interactive REPL mode (-i) is not supported in the Burp Python IDE runner.");
        }
        return List.copyOf(tokens);
    }

    private static List<String> tokenize(String commandLine) throws IOException {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        int backslashes = 0;
        for (int index = 0; index < commandLine.length(); index++) {
            char ch = commandLine.charAt(index);
            if (ch == '\\') {
                backslashes++;
                current.append(ch);
                continue;
            }
            if (ch == '"') {
                if ((backslashes % 2) == 0) {
                    current.setLength(current.length() - backslashes);
                    current.append("\\".repeat(backslashes / 2));
                    inQuotes = !inQuotes;
                } else {
                    current.setLength(current.length() - backslashes);
                    current.append("\\".repeat(backslashes / 2));
                    current.append('"');
                }
                backslashes = 0;
                continue;
            }
            backslashes = 0;
            if (Character.isWhitespace(ch) && !inQuotes) {
                flush(tokens, current);
                continue;
            }
            current.append(ch);
        }
        if (inQuotes) {
            throw new IOException("Custom command contains an unmatched quote.");
        }
        flush(tokens, current);
        return tokens;
    }

    private static void flush(List<String> tokens, StringBuilder current) {
        if (current.isEmpty()) {
            return;
        }
        tokens.add(current.toString());
        current.setLength(0);
    }
}
