package com.crawlfilter;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.editor.HttpRequestEditor;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static burp.api.montoya.ui.editor.EditorOptions.READ_ONLY;

public final class CrawlFilterPanel extends JPanel
{
    private final RequestTableModel tableModel = new RequestTableModel();
    private final HttpRequestEditor requestViewer;
    private final JTable table;
    private final TableRowSorter<RequestTableModel> sorter;
    private final JLabel stateValue = new JLabel();
    private final JLabel modeValue = new JLabel();
    private final JLabel seenValue = new JLabel("0");
    private final JLabel uniqueValue = new JLabel("0");
    private final JLabel duplicateValue = new JLabel("0");
    private final JLabel ignoredStaticValue = new JLabel("0");

    public CrawlFilterPanel(
            MontoyaApi api,
            Runnable clearAction,
            Consumer<Boolean> captureChanged,
            Consumer<Boolean> includeHostChanged
    )
    {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        requestViewer = api.userInterface().createHttpRequestEditor(READ_ONLY);
        requestViewer.setRequest(HttpRequest.httpRequest());

        JLabel description = new JLabel(
                "Logs only in-scope Proxy requests. Ignores .js/.gif/.jpg/.png/.css/.json/.map/.svg and dedupes by method + path."
        );

        JTextField searchField = new JTextField(28);

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(event -> clearAction.run());

        JToggleButton captureToggle = new JToggleButton();
        captureToggle.setSelected(true);
        updateCaptureToggleText(captureToggle, true);
        captureToggle.addActionListener(event ->
        {
            boolean enabled = captureToggle.isSelected();
            updateCaptureToggleText(captureToggle, enabled);
            captureChanged.accept(enabled);
        });

        JCheckBox includeHostCheckbox = new JCheckBox("Include host in dedupe key");
        includeHostCheckbox.addActionListener(event -> includeHostChanged.accept(includeHostCheckbox.isSelected()));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.add(new JLabel("Search"));
        controls.add(searchField);
        controls.add(clearButton);
        controls.add(captureToggle);
        controls.add(includeHostCheckbox);

        JPanel stats = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        stats.add(new JLabel("State:"));
        stats.add(stateValue);
        stats.add(new JLabel("Mode:"));
        stats.add(modeValue);
        stats.add(new JLabel("Seen:"));
        stats.add(seenValue);
        stats.add(new JLabel("Unique:"));
        stats.add(uniqueValue);
        stats.add(new JLabel("Duplicates:"));
        stats.add(duplicateValue);
        stats.add(new JLabel("Ignored Static:"));
        stats.add(ignoredStaticValue);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        stats.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(description);
        header.add(Box.createVerticalStrut(8));
        header.add(controls);
        header.add(Box.createVerticalStrut(6));
        header.add(stats);

        add(header, BorderLayout.NORTH);

        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        table.getSelectionModel().addListSelectionListener(event ->
        {
            if (event.getValueIsAdjusting())
            {
                return;
            }

            int viewRow = table.getSelectedRow();
            if (viewRow < 0)
            {
                requestViewer.setRequest(HttpRequest.httpRequest());
                return;
            }

            int modelRow = table.convertRowIndexToModel(viewRow);
            RequestEntry entry = tableModel.getEntry(modelRow);
            requestViewer.setRequest(entry.request());
        });

        configureColumnWidths();
        installSearchFilter(searchField);

        JScrollPane tableScrollPane = new JScrollPane(table);

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                tableScrollPane,
                requestViewer.uiComponent()
        );
        splitPane.setResizeWeight(0.45);

        add(splitPane, BorderLayout.CENTER);

        updateStats(0, 0, 0, 0, true, false);
    }

    public void addEntry(RequestEntry entry)
    {
        tableModel.addEntry(entry);
    }

    public void clearEntries()
    {
        table.clearSelection();
        tableModel.clearEntries();
        requestViewer.setRequest(HttpRequest.httpRequest());
    }

    public void scrollToLatest()
    {
        int lastViewRow = table.getRowCount() - 1;
        if (lastViewRow < 0)
        {
            return;
        }

        Rectangle rectangle = table.getCellRect(lastViewRow, 0, true);
        table.scrollRectToVisible(rectangle);
    }

    public void updateStats(
            long total,
            long unique,
            long duplicates,
            long ignoredStatic,
            boolean captureEnabled,
            boolean includeHost
    )
    {
        seenValue.setText(Long.toString(total));
        uniqueValue.setText(Long.toString(unique));
        duplicateValue.setText(Long.toString(duplicates));
        ignoredStaticValue.setText(Long.toString(ignoredStatic));
        stateValue.setText(captureEnabled ? "Capturing" : "Paused");
        modeValue.setText(includeHost ? "host + method + path" : "method + path");
    }

    private void configureColumnWidths()
    {
        table.getColumnModel().getColumn(0).setPreferredWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(155);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);
        table.getColumnModel().getColumn(4).setPreferredWidth(220);
        table.getColumnModel().getColumn(5).setPreferredWidth(80);
        table.getColumnModel().getColumn(6).setPreferredWidth(320);
        table.getColumnModel().getColumn(7).setPreferredWidth(280);
        table.getColumnModel().getColumn(8).setPreferredWidth(640);
    }

    private void installSearchFilter(JTextField searchField)
    {
        searchField.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent event)
            {
                applyFilter(searchField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent event)
            {
                applyFilter(searchField.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent event)
            {
                applyFilter(searchField.getText());
            }
        });
    }

    private void applyFilter(String text)
    {
        if (text == null || text.isBlank())
        {
            sorter.setRowFilter(null);
            return;
        }

        sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
    }

    private void updateCaptureToggleText(AbstractButton button, boolean enabled)
    {
        button.setText(enabled ? "Capturing" : "Paused");
    }
}
