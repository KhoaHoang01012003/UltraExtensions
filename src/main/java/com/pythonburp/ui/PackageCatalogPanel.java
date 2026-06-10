package com.pythonburp.ui;

import com.pythonburp.catalog.PackageCatalog;
import com.pythonburp.catalog.PackageCatalogEntry;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.awt.BorderLayout;

public final class PackageCatalogPanel extends JPanel {
    public PackageCatalogPanel(PackageCatalog catalog) {
        super(new BorderLayout());
        String[] columns = {"Package", "Version", "Tier", "Native"};
        Object[][] rows = catalog.entries().stream()
            .map(this::row)
            .toArray(Object[][]::new);
        add(new JScrollPane(new JTable(rows, columns)), BorderLayout.CENTER);
    }

    private Object[] row(PackageCatalogEntry entry) {
        return new Object[]{entry.name(), entry.version(), entry.tier(), entry.nativeRequired()};
    }
}
