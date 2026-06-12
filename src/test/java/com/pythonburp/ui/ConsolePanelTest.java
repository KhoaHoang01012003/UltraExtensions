package com.pythonburp.ui;

import com.pythonburp.console.ConsoleEvent;
import com.pythonburp.console.ConsoleEventType;
import org.junit.jupiter.api.Test;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ConsolePanelTest {
    @Test
    void clearRemovesConsoleOutput() throws Exception {
        AtomicReference<String> text = new AtomicReference<>();
        onEdt(() -> {
            ConsolePanel panel = new ConsolePanel();
            panel.appendSystem("hello");
            panel.append(List.of(ConsoleEvent.now(ConsoleEventType.STDERR, "boom")));

            panel.clear();

            text.set(findTextArea(panel).getText());
        });

        assertEquals("", text.get());
    }

    private static JTextArea findTextArea(ConsolePanel panel) {
        return (JTextArea) ((javax.swing.JScrollPane) panel.getComponent(0)).getViewport().getView();
    }

    private static void onEdt(Runnable runnable) throws InvocationTargetException, InterruptedException {
        SwingUtilities.invokeAndWait(runnable);
    }
}
