package com.crawlfilter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ClipboardFormatterTest
{
    @Test
    void formatsOnlyRequestRowsWithoutHeaderLine()
    {
        RequestEntry first = new RequestEntry(1L, "2026-06-04 10:00:00", "GET", "https", "example.com", 443,
                "/api/users", "page=1", "https://example.com/api/users?page=1", null);
        RequestEntry second = new RequestEntry(2L, "2026-06-04 10:00:01", "POST", "https", "example.com", 443,
                "/api/login", "", "https://example.com/api/login", null);

        String copied = ClipboardFormatter.format(
                List.of(2, 6, 7, 8),
                List.of(first, second)
        );

        assertEquals(
                String.join(System.lineSeparator(),
                        "GET\t/api/users\tpage=1\thttps://example.com/api/users?page=1",
                        "POST\t/api/login\t\thttps://example.com/api/login"
                ),
                copied
        );
    }

    @Test
    void combinesPathAndQueryWhenQueryColumnIsHidden()
    {
        RequestEntry entry = new RequestEntry(1L, "2026-06-04 10:00:00", "GET", "https", "example.com", 443,
                "/api/users", "page=1", "https://example.com/api/users?page=1", null);

        String copied = ClipboardFormatter.format(
                List.of(2, 6, 8),
                List.of(entry)
        );

        assertEquals(
                "GET\t/api/users?page=1\thttps://example.com/api/users?page=1",
                copied
        );
    }
}
