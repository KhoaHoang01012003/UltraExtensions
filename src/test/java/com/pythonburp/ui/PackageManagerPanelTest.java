package com.pythonburp.ui;

import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class PackageManagerPanelTest {
    @Test
    void containsApprovedPackageActions() throws Exception {
        AtomicReference<List<String>> labels = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> labels.set(buttonLabels(new PackageManagerPanel())));

        assertTrue(labels.get().containsAll(List.of(
            "Install", "Install Wheel", "Install Requirements", "Refresh", "Settings",
            "Clear User Packages", "Clear pip Cache", "Reset All Extension Data"
        )));
    }

    private static List<String> buttonLabels(Container root) {
        List<String> labels = new ArrayList<>();
        visit(root, component -> {
            if (component instanceof AbstractButton button) labels.add(button.getText());
        });
        return labels;
    }

    private static void visit(Component component, java.util.function.Consumer<Component> visitor) {
        visitor.accept(component);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) visit(child, visitor);
        }
    }
}
