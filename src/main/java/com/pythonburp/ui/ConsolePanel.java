package com.pythonburp.ui;

import com.pythonburp.console.ConsoleEvent;
import com.pythonburp.console.ConsoleEventType;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class ConsolePanel extends JPanel {
    private final JTextArea output = new JTextArea();
    private final JPanel inputPanel = new JPanel(new BorderLayout(8, 0));
    private final JLabel promptLabel = new JLabel("Input");
    private final JTextField inputField = new JTextField();
    private final JButton submitButton = new JButton("Send");
    private final JButton cancelButton = new JButton("Cancel");
    private CompletableFuture<String> pendingInput;

    public ConsolePanel() {
        super(new BorderLayout());
        output.setEditable(false);
        add(new JScrollPane(output), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.add(submitButton);
        actions.add(cancelButton);
        inputPanel.add(promptLabel, BorderLayout.WEST);
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(actions, BorderLayout.EAST);
        inputPanel.setVisible(false);
        add(inputPanel, BorderLayout.SOUTH);

        submitButton.addActionListener(event -> submitPendingInput());
        cancelButton.addActionListener(event -> cancelPendingInput("Interactive input canceled by user."));
        inputField.addActionListener(event -> submitPendingInput());
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

    public String requestInput(String prompt) throws IOException, InterruptedException {
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            SwingUtilities.invokeAndWait(() -> beginPrompt(prompt, future));
            return future.get();
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new IOException("Failed to open interactive input prompt.", e.getCause());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CancellationException cancellation) {
                throw new IOException(cancellation.getMessage());
            }
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("Interactive input failed.", cause);
        } catch (InterruptedException e) {
            future.cancel(true);
            SwingUtilities.invokeLater(this::resetPrompt);
            throw e;
        }
    }

    public void cancelPendingInput(String reason) {
        if (SwingUtilities.isEventDispatchThread()) {
            cancelPendingInputEdt(reason);
        } else {
            SwingUtilities.invokeLater(() -> cancelPendingInputEdt(reason));
        }
    }

    public void clear() {
        output.setText("");
    }

    private void beginPrompt(String prompt, CompletableFuture<String> future) {
        cancelPendingInputEdt("A new interactive prompt replaced the previous one.");
        pendingInput = future;
        promptLabel.setText(prompt == null || prompt.isBlank() ? "Input" : prompt);
        inputField.setText("");
        inputPanel.setVisible(true);
        appendSystem("Interactive input requested" + ((prompt == null || prompt.isBlank()) ? "" : ": " + prompt));
        inputField.requestFocusInWindow();
    }

    private void submitPendingInput() {
        if (pendingInput == null || pendingInput.isDone()) {
            return;
        }
        String value = inputField.getText();
        output.append((promptLabel.getText().equals("Input") ? "" : promptLabel.getText() + " ") + value + "\n");
        pendingInput.complete(value);
        resetPrompt();
    }

    private void cancelPendingInputEdt(String reason) {
        if (pendingInput != null && !pendingInput.isDone()) {
            pendingInput.completeExceptionally(new CancellationException(reason));
        }
        resetPrompt();
    }

    private void resetPrompt() {
        pendingInput = null;
        inputField.setText("");
        inputPanel.setVisible(false);
    }
}
