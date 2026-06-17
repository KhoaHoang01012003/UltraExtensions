package com.pythonburp.repeater;

import com.pythonburp.concurrency.Edt;

import javax.swing.JTabbedPane;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RepeaterWorkspaceScanner {
    private static final int MAX_NODES = 5_000;

    public List<RepeaterTabSnapshot> scanOpenTabs() {
        List<RepeaterTabSnapshot> snapshots = new ArrayList<>();
        Map<Object, Boolean> visited = new IdentityHashMap<>();

        List<Window> windows = new ArrayList<>();
        Edt.runAndWait(() -> {
            for (Window window : Window.getWindows()) {
                if (window != null) {
                    windows.add(window);
                }
            }
        });

        for (Window window : windows) {
            if (window == null || !window.isDisplayable()) {
                continue;
            }
            String windowTitle = resolveWindowTitle(window);
            scanTree(window, windowTitle, snapshots, visited);
        }

        return snapshots;
    }

    private void scanTree(Object root, String windowTitle, List<RepeaterTabSnapshot> snapshots,
                          Map<Object, Boolean> visited) {
        Deque<ScanFrame> stack = new ArrayDeque<>();
        stack.push(new ScanFrame(root, "", textLooksLikeRepeater(windowTitle)));

        int visitedCount = 0;
        while (!stack.isEmpty() && visitedCount < MAX_NODES) {
            ScanFrame frame = stack.pop();
            Object candidate = frame.node();
            if (candidate == null || visited.putIfAbsent(candidate, Boolean.TRUE) != null) {
                continue;
            }
            visitedCount++;

            boolean repeaterContext = frame.repeaterContext()
                || textLooksLikeRepeater(windowTitle)
                || nameLooksLikeRepeater(candidate);

            if (candidate instanceof JTextComponent textComponent) {
                String requestText = textComponent.getText();
                if (repeaterContext && looksLikeHttpRequest(requestText)) {
                    snapshots.add(new RepeaterTabSnapshot(
                        windowTitle,
                        formatPath(windowTitle, frame.path()),
                        textComponent,
                        requestText
                    ));
                }
                continue;
            }

            if (candidate instanceof JTabbedPane tabbedPane) {
                for (int i = tabbedPane.getTabCount() - 1; i >= 0; i--) {
                    Component child = tabbedPane.getComponentAt(i);
                    String title = safeText(tabbedPane.getTitleAt(i));
                    String nextPath = appendPath(frame.path(), title);
                    boolean nextContext = repeaterContext || textLooksLikeRepeater(title);
                    stack.push(new ScanFrame(child, nextPath, nextContext));
                }
                continue;
            }

            if (candidate instanceof Container container) {
                Component[] children = container.getComponents();
                for (int i = children.length - 1; i >= 0; i--) {
                    stack.push(new ScanFrame(children[i], frame.path(), repeaterContext));
                }
            }
        }
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

    private static String resolveWindowTitle(Window window) {
        String title = window instanceof java.awt.Frame frame ? frame.getTitle() : window.getName();
        if (title == null || title.isBlank()) {
            title = window.getClass().getSimpleName();
        }
        return title;
    }

    private static boolean nameLooksLikeRepeater(Object candidate) {
        if (candidate instanceof Component component) {
            String name = component.getName();
            return textLooksLikeRepeater(name);
        }
        return false;
    }

    private static boolean textLooksLikeRepeater(String text) {
        return safeText(text).toLowerCase(Locale.ROOT).contains("repeater");
    }

    private static String appendPath(String currentPath, String next) {
        if (next.isBlank()) {
            return currentPath;
        }
        if (currentPath.isBlank()) {
            return next;
        }
        return currentPath + " / " + next;
    }

    private static String formatPath(String windowTitle, String path) {
        if (path.isBlank()) {
            return windowTitle;
        }
        if (path.equals(windowTitle)) {
            return path;
        }
        return windowTitle + " / " + path;
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private record ScanFrame(Object node, String path, boolean repeaterContext) {
    }
}
