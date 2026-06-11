package com.pythonburp.ui;

import com.pythonburp.catalog.PackageCatalog;
import com.pythonburp.catalog.PackageCatalogEntry;
import com.pythonburp.catalog.PackageDiagnosticResult;
import com.pythonburp.catalog.PackageDiagnosticStatus;
import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PackageCatalogPanelTest {
    @Test
    void updatesRowsWithDiagnosticStatuses() throws Exception {
        AtomicReference<Object> firstStatus = new AtomicReference<>();
        AtomicReference<Object> secondStatus = new AtomicReference<>();
        AtomicReference<Object> firstDetails = new AtomicReference<>();
        AtomicReference<Object> secondDetails = new AtomicReference<>();
        onEdt(() -> {
            PackageCatalogEntry ok = new PackageCatalogEntry("ok", "1", "pure-python", false, "import ok");
            PackageCatalogEntry bad = new PackageCatalogEntry(
                "bad",
                "1",
                "native-candidate",
                true,
                java.util.Optional.of("windows-x64-core"),
                "import bad"
            );
            PackageCatalogPanel panel = new PackageCatalogPanel(new PackageCatalog(List.of(ok, bad)));

            panel.updateDiagnostics(List.of(
                new PackageDiagnosticResult(ok, PackageDiagnosticStatus.PASSED, "ok", "", ""),
                new PackageDiagnosticResult(bad, PackageDiagnosticStatus.FAILED, "", "", "boom")
            ));
            JTable table = findTable(panel);
            firstStatus.set(table.getValueAt(0, 5));
            secondStatus.set(table.getValueAt(1, 5));
            firstDetails.set(table.getValueAt(0, 6));
            secondDetails.set(table.getValueAt(1, 6));
        });

        assertEquals(PackageDiagnosticStatus.PASSED, firstStatus.get());
        assertEquals(PackageDiagnosticStatus.FAILED, secondStatus.get());
        assertEquals("ok", firstDetails.get());
        assertEquals("boom", secondDetails.get());
    }

    @Test
    void marksRowsAsRunningBeforeDiagnosticsFinish() throws Exception {
        AtomicReference<Object> status = new AtomicReference<>();
        AtomicReference<Object> details = new AtomicReference<>();
        onEdt(() -> {
            PackageCatalogEntry entry = new PackageCatalogEntry("pkg", "1", "pure-python", false, "import pkg");
            PackageCatalogPanel panel = new PackageCatalogPanel(new PackageCatalog(List.of(entry)));

            panel.markRunning();

            JTable table = findTable(panel);
            status.set(table.getValueAt(0, 5));
            details.set(table.getValueAt(0, 6));
        });

        assertEquals(PackageDiagnosticStatus.RUNNING, status.get());
        assertEquals("Running smoke tests", details.get());
    }

    @Test
    void showsNativePackIdWhenPresent() throws Exception {
        AtomicReference<Object> nativePack = new AtomicReference<>();
        onEdt(() -> {
            PackageCatalogEntry entry = new PackageCatalogEntry(
                "native-pkg",
                "1",
                "native-candidate",
                true,
                java.util.Optional.of("windows-x64-core"),
                "import native_pkg"
            );
            PackageCatalogPanel panel = new PackageCatalogPanel(new PackageCatalog(List.of(entry)));

            nativePack.set(findTable(panel).getValueAt(0, 4));
        });

        assertEquals("windows-x64-core", nativePack.get());
    }

    private static JTable findTable(PackageCatalogPanel panel) {
        return (JTable) ((javax.swing.JScrollPane) panel.getComponent(1)).getViewport().getView();
    }

    private static void onEdt(Runnable runnable) throws InvocationTargetException, InterruptedException {
        SwingUtilities.invokeAndWait(runnable);
    }
}
