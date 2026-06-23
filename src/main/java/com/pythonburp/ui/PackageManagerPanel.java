package com.pythonburp.ui;

import com.pythonburp.packages.PackageInventoryEntry;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;

public final class PackageManagerPanel extends JPanel {
    private static final int MAX_OUTPUT_CHARS = 200_000;
    final JTextField requirement = new JTextField();
    final JButton install = new JButton("Install");
    final JButton installWheel = new JButton("Install Wheel");
    final JButton installRequirements = new JButton("Install Requirements");
    final JButton refresh = new JButton("Refresh");
    final JButton settings = new JButton("Settings");
    final JButton uninstall = new JButton("Uninstall Selected");
    final JButton clearPackages = new JButton("Clear User Packages");
    final JButton clearPipCache = new JButton("Clear pip Cache");
    final JButton resetAll = new JButton("Reset All Extension Data");
    final JLabel status = new JLabel("Ready");
    final DefaultTableModel model = new DefaultTableModel(
        new Object[]{"Name", "Active Version", "Source", "Bundled Fallback", "Native"}, 0
    ) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    final JTable table = new JTable(model);
    final JTextArea output = new JTextArea();

    public PackageManagerPanel() {
        super(new BorderLayout(6, 6));
        JPanel installRow = new JPanel(new BorderLayout(6, 0));
        installRow.add(requirement, BorderLayout.CENTER);
        installRow.add(install, BorderLayout.EAST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.add(installWheel);
        actions.add(installRequirements);
        actions.add(refresh);
        actions.add(settings);
        actions.add(uninstall);

        JPanel header = new JPanel(new GridLayout(0, 1, 0, 6));
        header.add(new JLabel("Installing packages runs third-party code. Review package sources before installing."));
        header.add(installRow);
        header.add(actions);

        output.setEditable(false);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), new JScrollPane(output));
        split.setResizeWeight(0.68);

        JPanel cleanup = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        cleanup.add(clearPackages);
        cleanup.add(clearPipCache);
        cleanup.add(resetAll);
        cleanup.add(status);

        add(header, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(cleanup, BorderLayout.SOUTH);
    }

    void setInventory(List<PackageInventoryEntry> entries) {
        model.setRowCount(0);
        for (PackageInventoryEntry entry : entries) {
            model.addRow(new Object[]{entry.name(), entry.activeVersion(), entry.source(),
                entry.bundledFallback(), entry.nativeFiles()});
        }
    }

    void setBusy(boolean busy, String text) {
        for (JButton button : List.of(install, installWheel, installRequirements, refresh, settings,
            uninstall, clearPackages, clearPipCache, resetAll)) button.setEnabled(!busy);
        status.setText(text);
    }

    void appendOutput(String line) {
        output.append(line);
        if (!line.endsWith("\n")) output.append("\n");
        int excess = output.getDocument().getLength() - MAX_OUTPUT_CHARS;
        if (excess > 0) {
            try { output.getDocument().remove(0, excess); }
            catch (javax.swing.text.BadLocationException ignored) { }
        }
        output.setCaretPosition(output.getDocument().getLength());
    }

    String selectedPackage() {
        int row = table.getSelectedRow();
        return row < 0 ? "" : String.valueOf(model.getValueAt(row, 0));
    }
}
