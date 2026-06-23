package com.pythonburp.console;

import java.time.Instant;
import java.util.Objects;

public record ConsoleEvent(ConsoleEventType type, String text, Instant timestamp) {
    public ConsoleEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    public static ConsoleEvent now(ConsoleEventType type, String text) {
        return new ConsoleEvent(type, text, Instant.now());
    }
}
