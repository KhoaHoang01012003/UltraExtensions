package com.pythonburp.ui;

import com.pythonburp.repeater.RepeaterTabSnapshot;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

public final class RepeaterBatchPanel extends JPanel {
    private static final int MAX_OUTPUT_CHARS = 200_000;

    final JButton scan = new JButton("Scan Open Tabs");
    final JButton normalizeSelected = new JButton("Normalize Selected");
    final JButton normalizeAll = new JButton("Normalize All");
    final JButton clear = new JButton("Clear Results");
    final JLabel status = new JLabel("Ready");
    final DefaultTableModel model = new DefaultTableModel(
        new Object[]{"Use", "Window", "Tab", "Request"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : String.class;
        }
    };
    final JTable table = new JTable(model);
    final JTextArea output = new JTextArea();
    private final List<RepeaterTabSnapshot> snapshots = new ArrayList<>();

    public RepeaterBatchPanel() {
        super(new BorderLayout(6, 6));
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.add(scan);
        actions.add(normalizeSelected);
        actions.add(normalizeAll);
        actions.add(clear);
        actions.add(status);

        output.setEditable(false);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(new JScrollPane(output), BorderLayout.SOUTH);
        add(actions, BorderLayout.NORTH);
    }

    void setSnapshots(List<RepeaterTabSnapshot> values) {
        snapshots.clear();
        snapshots.addAll(values);
        model.setRowCount(0);
        for (RepeaterTabSnapshot snapshot : snapshots) {
            model.addRow(new Object[]{Boolean.TRUE, snapshot.windowTitle(), snapshot.tabPath(),
                summarize(snapshot.requestText())});
        }
        setStatus("Found " + snapshots.size() + " candidate tabs");
    }

    List<RepeaterTabSnapshot> selectedSnapshots() {
        List<RepeaterTabSnapshot> selected = new ArrayList<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            Object use = model.getValueAt(row, 0);
            if (Boolean.TRUE.equals(use) && row < snapshots.size()) {
                selected.add(snapshots.get(row));
            }
        }
        return selected;
    }

    List<RepeaterTabSnapshot> allSnapshots() {
        return List.copyOf(snapshots);
    }

    void setBusy(boolean busy, String text) {
        scan.setEnabled(!busy);
        normalizeSelected.setEnabled(!busy);
        normalizeAll.setEnabled(!busy);
        clear.setEnabled(!busy);
        setStatus(text);
    }

    void setStatus(String text) {
        status.setText(text);
    }

    void appendOutput(String line) {
        output.append(line);
        if (!line.endsWith("\n")) {
            output.append("\n");
        }
        int excess = output.getDocument().getLength() - MAX_OUTPUT_CHARS;
        if (excess > 0) {
            try {
                output.getDocument().remove(0, excess);
            } catch (javax.swing.text.BadLocationException ignored) {
            }
        }
        output.setCaretPosition(output.getDocument().getLength());
    }

    void clearResults() {
        model.setRowCount(0);
        snapshots.clear();
        output.setText("");
        setStatus("Ready");
    }

    private static String summarize(String requestText) {
        if (requestText == null || requestText.isBlank()) {
            return "(empty)";
        }
        String firstLine = requestText.lines().findFirst().orElse("").trim();
        return firstLine.length() > 120 ? firstLine.substring(0, 117) + "..." : firstLine;
    }
}
