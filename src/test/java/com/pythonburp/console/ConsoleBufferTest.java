package com.pythonburp.console;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConsoleBufferTest {
    @Test
    void drainReturnsEventsInOrder() {
        ConsoleBuffer buffer = new ConsoleBuffer(10);
        buffer.append(ConsoleEventType.STDOUT, "one");
        buffer.append(ConsoleEventType.STDERR, "two");

        List<ConsoleEvent> events = buffer.drain();

        assertEquals("one", events.get(0).text());
        assertEquals(ConsoleEventType.STDERR, events.get(1).type());
        assertTrue(buffer.drain().isEmpty());
    }

    @Test
    void bufferDropsOldestEventsWhenCapacityIsExceeded() {
        ConsoleBuffer buffer = new ConsoleBuffer(2);
        buffer.append(ConsoleEventType.STDOUT, "one");
        buffer.append(ConsoleEventType.STDOUT, "two");
        buffer.append(ConsoleEventType.STDOUT, "three");

        List<ConsoleEvent> events = buffer.drain();

        assertEquals(2, events.size());
        assertEquals("two", events.get(0).text());
        assertEquals("three", events.get(1).text());
        assertEquals(1, buffer.droppedCount());
    }
}
