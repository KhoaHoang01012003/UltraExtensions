package com.pythonburp.ui;

import com.pythonburp.console.ConsoleEvent;
import com.pythonburp.console.ConsoleEventType;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.util.List;

public final class ConsolePanel extends JPanel {
    private final JTextArea output = new JTextArea();

    public ConsolePanel() {
        super(new BorderLayout());
        output.setEditable(false);
        add(new JScrollPane(output), BorderLayout.CENTER);
    }

    public void append(List<ConsoleEvent> events) {
        for (ConsoleEvent event : events) {
            String prefix = event.type() == ConsoleEventType.STDERR ? "[err] " : "";
            output.append(prefix + event.text());
            if (!event.text().endsWith("\n")) {
                output.append("\n");
            }
        }
    }

    public void appendSystem(String text) {
        output.append("[system] " + text + "\n");
    }

    public void clear() {
        output.setText("");
    }
}
