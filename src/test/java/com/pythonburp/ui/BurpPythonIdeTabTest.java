package com.pythonburp.ui;

import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.catalog.PackageCatalog;
import com.pythonburp.concurrency.RuntimeActivityCoordinator;
import com.pythonburp.concurrency.IdeExecutors;
import com.pythonburp.packages.EmbeddedPipRunner;
import com.pythonburp.packages.PackageInventoryReader;
import com.pythonburp.packages.PackageManagerService;
import com.pythonburp.packages.PackageRequestStore;
import com.pythonburp.packages.PackageSettingsStore;
import com.pythonburp.packages.SharedPackageEnvironment;
import com.pythonburp.python.CPythonRuntimeFactory;
import com.pythonburp.python.PythonRuntimeEnvironment;
import com.pythonburp.storage.ExtensionDataCleaner;
import com.pythonburp.storage.ExtensionDataPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class BurpPythonIdeTabTest {
    @TempDir
    Path tempDir;

    @Test
    void toolbarContainsFileAndConsoleActions() throws Exception {
        AtomicBoolean hasLoad = new AtomicBoolean(false);
        AtomicBoolean hasSaveAs = new AtomicBoolean(false);
        AtomicBoolean hasClearLog = new AtomicBoolean(false);
        AtomicBoolean hasHelp = new AtomicBoolean(false);

        onEdt(() -> {
            BurpPythonIdeTab tab = newTab().orElseThrow();
            List<String> labels = buttonLabels(tab);
            hasLoad.set(labels.contains("Load"));
            hasSaveAs.set(labels.contains("Save As"));
            hasClearLog.set(labels.contains("Clear Log"));
            hasHelp.set(labels.contains("Help"));
        });

        assertTrue(hasLoad.get());
        assertTrue(hasSaveAs.get());
        assertTrue(hasClearLog.get());
        assertTrue(hasHelp.get());
    }

    @Test
    void containsEditorAndPackageManagerWorkspaceTabs() throws Exception {
        AtomicBoolean hasWorkspaces = new AtomicBoolean(false);

        onEdt(() -> {
            JTabbedPane tabs = findTabbedPane(newTab().orElseThrow());
            hasWorkspaces.set(tabs != null
                && tabs.indexOfTab("Editor") >= 0
                && tabs.indexOfTab("Package Manager") >= 0);
        });

        assertTrue(hasWorkspaces.get());
    }

    @Test
    void exposesCustomCommandControls() throws Exception {
        AtomicBoolean hasModeSelector = new AtomicBoolean(false);
        AtomicBoolean hasCommandField = new AtomicBoolean(false);

        onEdt(() -> {
            BurpPythonIdeTab tab = newTab().orElseThrow();
            hasModeSelector.set(containsComponentType(tab, JComboBox.class));
            hasCommandField.set(containsComponentType(tab, JTextField.class));
        });

        assertTrue(hasModeSelector.get());
        assertTrue(hasCommandField.get());
    }

    private java.util.Optional<BurpPythonIdeTab> newTab() {
        try {
            ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("data"));
            Path fakePython = Files.writeString(tempDir.resolve("python.exe"), "fake");
            Path helperRoot = Files.createDirectories(tempDir.resolve("helper-root"));
            CPythonRuntimeFactory runtimeFactory = new CPythonRuntimeFactory(
                new PythonRuntimeEnvironment(fakePython, 3, 14, 0, "Windows", "AMD64", true),
                paths,
                () -> helperRoot
            );
            RuntimeActivityCoordinator coordinator = new RuntimeActivityCoordinator();
            PackageManagerService packageService = new PackageManagerService(
                paths,
                coordinator,
                new SharedPackageEnvironment(paths, runtimeFactory.userPackages()),
                new PackageRequestStore(paths.packageRequests()),
                new PackageSettingsStore(paths.settings().resolve("pip.properties")),
                new PackageInventoryReader(new PackageCatalog(List.of())),
                new ExtensionDataCleaner(paths, runtimeFactory.userPackages()),
                new EmbeddedPipRunner(),
                runtimeFactory.userPackages(),
                new PackageCatalog(List.of()),
                true,
                runtimeFactory::pythonExecutable
            );
            return java.util.Optional.of(new BurpPythonIdeTab(
                new IdeExecutors(1),
                new BurpBridge(),
                paths,
                coordinator,
                runtimeFactory,
                packageService
            ));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    private static JTabbedPane findTabbedPane(Container root) {
        if (root instanceof JTabbedPane tabs) return tabs;
        for (Component child : root.getComponents()) {
            if (child instanceof Container container) {
                JTabbedPane found = findTabbedPane(container);
                if (found != null) return found;
            }
        }
        return null;
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
