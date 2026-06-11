package com.pythonburp.ui;

import com.pythonburp.catalog.PackageCatalog;
import com.pythonburp.catalog.PackageDiagnosticResult;
import com.pythonburp.catalog.PackageDiagnosticStatus;
import com.pythonburp.catalog.PackageCatalogEntry;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PackageCatalogPanel extends JPanel {
    private final PackageCatalog catalog;
    private final DefaultTableModel model;
    private final Map<String, PackageDiagnosticResult> diagnostics = new HashMap<>();

    public PackageCatalogPanel(PackageCatalog catalog) {
        this(catalog, null);
    }

    public PackageCatalogPanel(PackageCatalog catalog, Runnable diagnosticsAction) {
        super(new BorderLayout());
        this.catalog = catalog;
        String[] columns = {"Package", "Version", "Tier", "Native", "Native Pack", "Status", "Details"};
        this.model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);

        JButton runDiagnostics = new JButton("Run diagnostics");
        runDiagnostics.setEnabled(diagnosticsAction != null);
        if (diagnosticsAction != null) {
            runDiagnostics.addActionListener(event -> diagnosticsAction.run());
        }

        add(runDiagnostics, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        refreshRows();
    }

    public void markRunning() {
        diagnostics.clear();
        refreshRows(PackageDiagnosticStatus.RUNNING, "Running smoke tests");
    }

    public void updateDiagnostics(List<PackageDiagnosticResult> results) {
        diagnostics.clear();
        for (PackageDiagnosticResult result : results) {
            diagnostics.put(result.entry().name(), result);
        }
        refreshRows();
    }

    private Object[] row(PackageCatalogEntry entry) {
        PackageDiagnosticResult result = diagnostics.get(entry.name());
        PackageDiagnosticStatus status = result == null ? PackageDiagnosticStatus.NOT_RUN : result.status();
        String details = result == null ? "" : details(result);
        return new Object[]{
            entry.name(),
            entry.version(),
            entry.tier(),
            entry.nativeRequired(),
            entry.nativePackId().orElse(""),
            status,
            details
        };
    }

    private void refreshRows() {
        model.setRowCount(0);
        for (PackageCatalogEntry entry : catalog.entries()) {
            model.addRow(row(entry));
        }
    }

    private void refreshRows(PackageDiagnosticStatus status, String details) {
        model.setRowCount(0);
        for (PackageCatalogEntry entry : catalog.entries()) {
            model.addRow(new Object[]{
                entry.name(),
                entry.version(),
                entry.tier(),
                entry.nativeRequired(),
                entry.nativePackId().orElse(""),
                status,
                details
            });
        }
    }

    private static String details(PackageDiagnosticResult result) {
        if (result.status() == PackageDiagnosticStatus.PASSED) {
            return firstLine(result.stdout());
        }
        if (!result.errorMessage().isBlank()) {
            return firstLine(result.errorMessage());
        }
        return firstLine(result.stderr());
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        String line = newline >= 0 ? text.substring(0, newline) : text;
        return line.length() > 140 ? line.substring(0, 140) : line;
    }
}
