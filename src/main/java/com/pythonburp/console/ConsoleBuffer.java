package com.pythonburp.console;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class ConsoleBuffer {
    private final int maxEvents;
    private final ArrayDeque<ConsoleEvent> events = new ArrayDeque<>();
    private long dropped;

    public ConsoleBuffer(int maxEvents) {
        if (maxEvents < 1) {
            throw new IllegalArgumentException("maxEvents must be positive");
        }
        this.maxEvents = maxEvents;
    }

    public synchronized void append(ConsoleEventType type, String text) {
        if (events.size() == maxEvents) {
            events.removeFirst();
            dropped++;
        }
        events.addLast(ConsoleEvent.now(type, text));
    }

    public synchronized List<ConsoleEvent> drain() {
        List<ConsoleEvent> drained = new ArrayList<>(events);
        events.clear();
        return drained;
    }

    public synchronized long droppedCount() {
        return dropped;
    }
}
