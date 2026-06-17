package com.pythonburp.repeater;

import com.pythonburp.concurrency.Edt;

import javax.swing.JTabbedPane;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class RepeaterWorkspaceScanner {
    public List<RepeaterTabSnapshot> scanOpenTabs() {
        List<RepeaterTabSnapshot> snapshots = new ArrayList<>();
        Edt.runAndWait(() -> {
            IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
            IdentityHashMap<JTextComponent, Boolean> seenEditors = new IdentityHashMap<>();
            for (Window window : Window.getWindows()) {
                if (window == null || !window.isDisplayable()) {
                    continue;
                }
                String windowTitle = window instanceof java.awt.Frame frame ? frame.getTitle() : window.getName();
                if (windowTitle == null || windowTitle.isBlank()) {
                    windowTitle = window.getClass().getSimpleName();
                }
                scanObject(window, windowTitle, new ArrayList<>(), false, visited, seenEditors, snapshots);
            }
        });
        return snapshots;
    }

    private void scanObject(Object candidate, String windowTitle, List<String> path, boolean repeaterContext,
                            Map<Object, Boolean> visited, Map<JTextComponent, Boolean> seenEditors,
                            List<RepeaterTabSnapshot> snapshots) {
        if (candidate == null || visited.putIfAbsent(candidate, Boolean.TRUE) != null) {
            return;
        }

        boolean nextRepeaterContext = repeaterContext
            || textLooksLikeRepeater(windowTitle)
            || componentNameLooksLikeRepeater(candidate)
            || nameLooksLikeRepeater(candidate);

        if (candidate instanceof JTextComponent textComponent) {
            if ((nextRepeaterContext || pathIndicatesRepeater(path) || textLooksLikeRepeater(textComponent.getName()))
                && looksLikeHttpRequest(textComponent.getText())
                && seenEditors.putIfAbsent(textComponent, Boolean.TRUE) == null) {
                snapshots.add(new RepeaterTabSnapshot(windowTitle, String.join(" / ", path), textComponent,
                    textComponent.getText()));
            }
            return;
        }

        if (candidate instanceof JTabbedPane tabbedPane) {
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                Component child = tabbedPane.getComponentAt(i);
                String title = tabbedPane.getTitleAt(i);
                List<String> nextPath = new ArrayList<>(path);
                if (title != null && !title.isBlank()) {
                    nextPath.add(title);
                }
                scanObject(child, windowTitle, nextPath, nextRepeaterContext || textLooksLikeRepeater(title),
                    visited, seenEditors, snapshots);
            }
            return;
        }

        if (candidate instanceof Container container) {
            for (Component child : container.getComponents()) {
                scanObject(child, windowTitle, path, nextRepeaterContext, visited, seenEditors, snapshots);
            }
        }

        if (candidate.getClass().isArray()) {
            int length = Array.getLength(candidate);
            for (int i = 0; i < length; i++) {
                scanObject(Array.get(candidate, i), windowTitle, path, nextRepeaterContext, visited, seenEditors,
                    snapshots);
            }
        }

        if (candidate instanceof Collection<?> collection) {
            for (Object item : collection) {
                scanObject(item, windowTitle, path, nextRepeaterContext, visited, seenEditors, snapshots);
            }
        }

        if (candidate instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                scanObject(item, windowTitle, path, nextRepeaterContext, visited, seenEditors, snapshots);
            }
        }

        for (Field field : candidate.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                continue;
            }
            try {
                if (!field.canAccess(candidate)) {
                    field.setAccessible(true);
                }
                Object value = field.get(candidate);
                if (value != null) {
                    boolean fieldRepeater = nextRepeaterContext || fieldNameLooksLikeRepeater(field.getName());
                    scanObject(value, windowTitle, path, fieldRepeater, visited, seenEditors, snapshots);
                }
            } catch (InaccessibleObjectException | IllegalAccessException ignored) {
                // Best effort: Burp internals may keep fields inaccessible.
            } catch (RuntimeException ignored) {
                // Best effort only.
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

    private static boolean componentNameLooksLikeRepeater(Object candidate) {
        return candidate.getClass().getSimpleName().toLowerCase().contains("repeater");
    }

    private static boolean nameLooksLikeRepeater(Object candidate) {
        if (candidate instanceof Component component) {
            String name = component.getName();
            return name != null && name.toLowerCase().contains("repeater");
        }
        return false;
    }

    private static boolean fieldNameLooksLikeRepeater(String name) {
        return name != null && name.toLowerCase().contains("repeater");
    }

    private static boolean textLooksLikeRepeater(String text) {
        return text != null && text.toLowerCase().contains("repeater");
    }

    private static boolean pathIndicatesRepeater(List<String> path) {
        for (String element : path) {
            if (textLooksLikeRepeater(element)) {
                return true;
            }
        }
        return false;
    }
}
