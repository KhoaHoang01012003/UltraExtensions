package com.pythonburp.console;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void constructorRejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new ConsoleBuffer(0));
        assertThrows(IllegalArgumentException.class, () -> new ConsoleBuffer(-1));
    }

    @Test
    void bufferUsesConstantTimeBoundedStorage() throws ReflectiveOperationException {
        ConsoleBuffer buffer = new ConsoleBuffer(10);
        Field events = ConsoleBuffer.class.getDeclaredField("events");
        events.setAccessible(true);

        assertInstanceOf(ArrayDeque.class, events.get(buffer));
    }

    @Test
    void concurrentAppendAndDrainAccountsForEveryEvent() throws Exception {
        ConsoleBuffer buffer = new ConsoleBuffer(64);
        int appenders = 4;
        int eventsPerAppender = 250;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(appenders + 1);

        try {
            Future<Integer> drained = executor.submit(() -> {
                start.await();
                int count = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    count += buffer.drain().size();
                }
                return count + buffer.drain().size();
            });

            Future<?>[] appenderTasks = new Future<?>[appenders];
            for (int i = 0; i < appenders; i++) {
                int appenderId = i;
                appenderTasks[i] = executor.submit(() -> {
                    await(start);
                    for (int j = 0; j < eventsPerAppender; j++) {
                        buffer.append(ConsoleEventType.STDOUT, appenderId + ":" + j);
                    }
                });
            }

            start.countDown();
            for (Future<?> appenderTask : appenderTasks) {
                appenderTask.get(5, TimeUnit.SECONDS);
            }

            executor.shutdownNow();
            int drainedCount = assertDoesNotThrow(() -> drained.get(5, TimeUnit.SECONDS));

            assertEquals(appenders * eventsPerAppender, drainedCount + buffer.droppedCount());
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting to start", e);
        }
    }
}
