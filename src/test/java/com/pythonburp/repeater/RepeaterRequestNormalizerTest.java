package com.pythonburp.repeater;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class RepeaterRequestNormalizerTest {
    @Test
    void convertsGetQueryToPostFormBody() {
        String input = """
            GET /submit?a=1&b=2 HTTP/1.1
            Host: example.com

            """;

        String output = RepeaterRequestNormalizer.normalizeGetToPost(input).orElseThrow();

        assertTrue(output.startsWith("POST /submit HTTP/1.1"));
        assertTrue(output.contains("Content-Type: application/x-www-form-urlencoded"));
        assertTrue(output.contains("Content-Length: 7"));
        assertTrue(output.endsWith("a=1&b=2"));
    }
}
