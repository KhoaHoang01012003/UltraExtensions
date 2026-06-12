package com.pythonburp.ui;

import com.pythonburp.concurrency.IdeExecutors;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BurpPythonIdeTabTest {
    @Test
    void toolbarContainsFileAndConsoleActions() throws Exception {
        AtomicBoolean hasLoad = new AtomicBoolean(false);
        AtomicBoolean hasSaveAs = new AtomicBoolean(false);
        AtomicBoolean hasClearLog = new AtomicBoolean(false);

        onEdt(() -> {
            BurpPythonIdeTab tab = newTab();
            List<String> labels = buttonLabels(tab);
            hasLoad.set(labels.contains("Load"));
            hasSaveAs.set(labels.contains("Save As"));
            hasClearLog.set(labels.contains("Clear Log"));
        });

        assertTrue(hasLoad.get());
        assertTrue(hasSaveAs.get());
        assertTrue(hasClearLog.get());
    }

    @Test
    void packageDiagnosticPanelIsNotShownInMainIde() throws Exception {
        AtomicBoolean hasPackagePanel = new AtomicBoolean(true);

        onEdt(() -> hasPackagePanel.set(containsComponentType(newTab(), JTable.class)));

        assertFalse(hasPackagePanel.get());
    }

    private static BurpPythonIdeTab newTab() {
        return new BurpPythonIdeTab(new IdeExecutors(1), null);
    }

    private static List<String> buttonLabels(Container root) {
        List<String> labels = new ArrayList<>();
        visit(root, component -> {
            if (component instanceof AbstractButton button) {
                labels.add(button.getText());
            }
        });
        return labels;
    }

    private static boolean containsComponentType(Container root, Class<? extends JComponent> type) {
        AtomicBoolean found = new AtomicBoolean(false);
        visit(root, component -> {
            if (type.isInstance(component)) {
                found.set(true);
            }
        });
        return found.get();
    }

    private static void visit(Component component, java.util.function.Consumer<Component> visitor) {
        visitor.accept(component);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                visit(child, visitor);
            }
        }
    }

    private static void onEdt(Runnable runnable) throws InvocationTargetException, InterruptedException {
        SwingUtilities.invokeAndWait(runnable);
    }
}
