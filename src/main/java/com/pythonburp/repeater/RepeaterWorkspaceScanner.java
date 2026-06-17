package com.pythonburp.repeater;

import com.pythonburp.concurrency.Edt;

import javax.swing.JTabbedPane;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RepeaterWorkspaceScanner {
    public List<RepeaterTabSnapshot> scanOpenTabs() {
        List<RepeaterTabSnapshot> snapshots = new ArrayList<>();
        Edt.runAndWait(() -> {
            IdentityHashMap<JTextComponent, Boolean> seenEditors = new IdentityHashMap<>();
            for (Window window : Window.getWindows()) {
                if (window == null || !window.isDisplayable()) {
                    continue;
                }
                String windowTitle = window instanceof java.awt.Frame frame ? frame.getTitle() : window.getName();
                if (windowTitle == null || windowTitle.isBlank()) {
                    windowTitle = window.getClass().getSimpleName();
                }
                scanComponent(window, windowTitle, new ArrayList<>(), false, seenEditors, snapshots);
            }
        });
        return snapshots;
    }

    private boolean scanComponent(Component component, String windowTitle, List<String> path,
                                  boolean repeaterContext, Map<JTextComponent, Boolean> seenEditors,
                                  List<RepeaterTabSnapshot> snapshots) {
        if (component == null) {
            return false;
        }

        boolean nextRepeaterContext = repeaterContext || componentNameLooksLikeRepeater(component)
            || textLooksLikeRepeater(windowTitle);

        if (component instanceof JTabbedPane tabbedPane) {
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                Component child = tabbedPane.getComponentAt(i);
                String title = tabbedPane.getTitleAt(i);
                List<String> nextPath = new ArrayList<>(path);
                if (title != null && !title.isBlank()) {
                    nextPath.add(title);
                }
                boolean childRepeater = nextRepeaterContext || textLooksLikeRepeater(title);
                if (scanComponent(child, windowTitle, nextPath, childRepeater, seenEditors, snapshots)) {
                    continue;
                }
            }
            return false;
        }

        if (nextRepeaterContext) {
            JTextComponent editor = findRequestEditor(component);
            if (editor != null && seenEditors.putIfAbsent(editor, Boolean.TRUE) == null) {
                snapshots.add(new RepeaterTabSnapshot(windowTitle, String.join(" / ", path), editor, editor.getText()));
            }
        }

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                scanComponent(child, windowTitle, path, nextRepeaterContext, seenEditors, snapshots);
            }
        }
        return false;
    }

    private JTextComponent findRequestEditor(Component component) {
        if (component instanceof JTextComponent textComponent && looksLikeHttpRequest(textComponent.getText())) {
            return textComponent;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JTextComponent editor = findRequestEditor(child);
                if (editor != null) {
                    return editor;
                }
            }
        }
        return null;
    }

    private static boolean looksLikeHttpRequest(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.stripLeading();
        int lineBreak = normalized.indexOf('\n');
        String firstLine = lineBreak >= 0 ? normalized.substring(0, lineBreak).trim() : normalized.trim();
        return firstLine.matches("(?i)^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS|TRACE|CONNECT)\\s+\\S+\\s+HTTP/\\d(?:\\.\\d)?$");
    }

    private static boolean componentNameLooksLikeRepeater(Component component) {
        String name = component.getClass().getSimpleName().toLowerCase();
        return name.contains("repeater");
    }

    private static boolean textLooksLikeRepeater(String text) {
        return text != null && text.toLowerCase().contains("repeater");
    }
}
