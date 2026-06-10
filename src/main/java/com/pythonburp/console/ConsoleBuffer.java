package com.pythonburp.console;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

public final class ConsoleBuffer {
    private final int maxEvents;
    private final ConcurrentLinkedDeque<ConsoleEvent> events = new ConcurrentLinkedDeque<>();
    private final AtomicLong dropped = new AtomicLong();

    public ConsoleBuffer(int maxEvents) {
        if (maxEvents < 1) {
            throw new IllegalArgumentException("maxEvents must be positive");
        }
        this.maxEvents = maxEvents;
    }

    public void append(ConsoleEventType type, String text) {
        events.addLast(ConsoleEvent.now(type, text));
        while (events.size() > maxEvents) {
            ConsoleEvent removed = events.pollFirst();
            if (removed != null) {
                dropped.incrementAndGet();
            }
        }
    }

    public List<ConsoleEvent> drain() {
        List<ConsoleEvent> drained = new ArrayList<>();
        ConsoleEvent event;
        while ((event = events.pollFirst()) != null) {
            drained.add(event);
        }
        return drained;
    }

    public long droppedCount() {
        return dropped.get();
    }
}
