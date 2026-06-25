package com.crawlfilter;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.editor.HttpRequestEditor;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import static burp.api.montoya.ui.editor.EditorOptions.READ_ONLY;

public final class CrawlFilterPanel extends JPanel
{
    public record SendActions(
            Runnable repeaterAction,
            Runnable intruderAction,
            Runnable activeScanAction,
            Runnable passiveScanAction,
            Runnable decoderAction,
            Runnable comparerAction,
            Runnable organizerAction,
            Runnable exportAuthMatrixAction
    )
    {
    }

    private final RequestTableModel tableModel = new RequestTableModel();
    private final HttpRequestEditor requestViewer;
    private final JTable table;
    private final TableRowSorter<RequestTableModel> sorter;
    private final Consumer<List<Integer>> visibleColumnsChanged;
    private final TrackerConnectionConfig trackerConfig;
    private final Map<Integer, TableColumn> allColumns = new LinkedHashMap<>();
    private final List<Integer> columnOrder = new ArrayList<>();
    private final JLabel stateValue = new JLabel();
    private final JLabel modeValue = new JLabel();
    private final JLabel seenValue = new JLabel("0");
    private final JLabel uniqueValue = new JLabel("0");
    private final JLabel duplicateValue = new JLabel("0");
    private final JLabel ignoredStaticValue = new JLabel("0");
    private final JTextArea resultLogArea = new JTextArea();

    public CrawlFilterPanel(
            MontoyaApi api,
            CrawlFilterSettings settings,
            Runnable clearAction,
            Runnable clearRepeaterTabsAction,
            SendActions sendActions,
            Consumer<Boolean> captureChanged,
            Consumer<Boolean> includeHostChanged,
            Consumer<Boolean> staticFilterChanged,
            Function<String, String> staticSuffixesChanged,
            Consumer<List<Integer>> visibleColumnsChanged
    )
    {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        this.visibleColumnsChanged = visibleColumnsChanged;
        this.trackerConfig = new TrackerConnectionConfig(
                settings.trackerHost(),
                settings.trackerRoundId(),
                settings.trackerJwtToken()
        );

        requestViewer = api.userInterface().createHttpRequestEditor(READ_ONLY);
        requestViewer.setRequest(HttpRequest.httpRequest());

        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        table.getSelectionModel().addListSelectionListener(event ->
        {
            if (event.getValueIsAdjusting())
            {
                return;
            }

            int viewRow = table.getSelectionModel().getLeadSelectionIndex();
            if (viewRow < 0 || !table.isRowSelected(viewRow))
            {
                viewRow = table.getSelectedRow();
            }

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
        captureColumnDefinitions();
        applyVisibleColumns(settings.visibleColumnModelIndices(), false);
        installCopyShortcut();
        installTableContextMenu(sendActions);

        JLabel description = new JLabel(
                "Logs only in-scope Proxy requests. Right-click selected rows for Burp actions."
        );

        JTextField searchField = new JTextField(28);
        JTextField staticSuffixField = new JTextField(settings.staticSuffixes(), 28);

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(event -> clearAction.run());

        JButton clearRepeaterButton = new JButton("Clear Repeater");
        clearRepeaterButton.addActionListener(event -> clearRepeaterTabsAction.run());

        JButton clearResultsButton = new JButton("Clear Results");
        clearResultsButton.addActionListener(event -> clearResultLog());

        JButton actionsButton = new JButton("Actions");
        actionsButton.addActionListener(event -> showActionsMenu(actionsButton, 0, actionsButton.getHeight(), sendActions));

        JButton columnsButton = new JButton("Columns");
        columnsButton.addActionListener(event -> showColumnMenu(columnsButton));

        JToggleButton captureToggle = new JToggleButton();
        captureToggle.setSelected(settings.captureEnabled());
        updateCaptureToggleText(captureToggle, settings.captureEnabled());
        captureToggle.addActionListener(event ->
        {
            boolean enabled = captureToggle.isSelected();
            updateCaptureToggleText(captureToggle, enabled);
            captureChanged.accept(enabled);
        });

        JCheckBox includeHostCheckbox = new JCheckBox(
                "Include host in dedupe key",
                settings.includeHostInFingerprint()
        );
        includeHostCheckbox.addActionListener(event -> includeHostChanged.accept(includeHostCheckbox.isSelected()));

        JCheckBox staticFilterCheckbox = new JCheckBox(
                "Filter static suffixes",
                settings.staticFilterEnabled()
        );
        staticFilterCheckbox.addActionListener(event -> staticFilterChanged.accept(staticFilterCheckbox.isSelected()));

        JButton applySuffixesButton = new JButton("Apply Suffixes");
        Runnable applySuffixes = () -> staticSuffixField.setText(staticSuffixesChanged.apply(staticSuffixField.getText()));
        applySuffixesButton.addActionListener(event -> applySuffixes.run());
        staticSuffixField.addActionListener(event -> applySuffixes.run());

        JPanel mainControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        mainControls.add(new JLabel("Search"));
        mainControls.add(searchField);
        mainControls.add(clearButton);
        mainControls.add(clearRepeaterButton);
        mainControls.add(clearResultsButton);
        mainControls.add(actionsButton);
        mainControls.add(columnsButton);
        mainControls.add(captureToggle);
        mainControls.add(includeHostCheckbox);

        JPanel filterControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterControls.add(staticFilterCheckbox);
        filterControls.add(new JLabel("Suffixes"));
        filterControls.add(staticSuffixField);
        filterControls.add(applySuffixesButton);

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
        mainControls.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterControls.setAlignmentX(Component.LEFT_ALIGNMENT);
        stats.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(description);
        header.add(Box.createVerticalStrut(8));
        header.add(mainControls);
        header.add(Box.createVerticalStrut(6));
        header.add(filterControls);
        header.add(Box.createVerticalStrut(6));
        header.add(stats);

        add(header, BorderLayout.NORTH);
        installSearchFilter(searchField);

        JScrollPane tableScrollPane = new JScrollPane(table);
        configureResultLogArea();
        JScrollPane resultLogScrollPane = new JScrollPane(resultLogArea);

        JSplitPane lowerSplitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                requestViewer.uiComponent(),
                resultLogScrollPane
        );
        lowerSplitPane.setResizeWeight(0.65);

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                tableScrollPane,
                lowerSplitPane
        );
        splitPane.setResizeWeight(0.45);

        add(splitPane, BorderLayout.CENTER);

        updateStats(0, 0, 0, 0, settings.captureEnabled(), settings.includeHostInFingerprint());
    }

    public void addEntry(RequestEntry entry)
    {
        tableModel.addEntry(entry);
    }

    public void restoreEntries(List<RequestEntry> restoredEntries)
    {
        table.clearSelection();
        tableModel.setEntries(restoredEntries);
        requestViewer.setRequest(HttpRequest.httpRequest());
    }

    public List<RequestEntry> selectedEntries()
    {
        int[] selectedRows = table.getSelectedRows();
        List<RequestEntry> selectedEntries = new ArrayList<>(selectedRows.length);

        for (int selectedRow : selectedRows)
        {
            int modelRow = table.convertRowIndexToModel(selectedRow);
            selectedEntries.add(tableModel.getEntry(modelRow));
        }

        return selectedEntries;
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

    public void appendResultLog(String message)
    {
        if (message == null || message.isBlank())
        {
            return;
        }

        if (!resultLogArea.getText().isEmpty())
        {
            resultLogArea.append(System.lineSeparator());
        }

        resultLogArea.append(message);
        resultLogArea.setCaretPosition(resultLogArea.getDocument().getLength());
    }

    public void clearResultLog()
    {
        resultLogArea.setText("");
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

    private void configureResultLogArea()
    {
        resultLogArea.setEditable(false);
        resultLogArea.setLineWrap(false);
        resultLogArea.setWrapStyleWord(false);
        resultLogArea.setRows(8);
        resultLogArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        resultLogArea.setText("Result log will appear here.");
        resultLogArea.setCaretPosition(resultLogArea.getDocument().getLength());
    }

    private void captureColumnDefinitions()
    {
        TableColumnModel columnModel = table.getColumnModel();
        for (int viewIndex = 0; viewIndex < columnModel.getColumnCount(); viewIndex++)
        {
            TableColumn column = columnModel.getColumn(viewIndex);
            allColumns.put(column.getModelIndex(), column);
            columnOrder.add(column.getModelIndex());
        }
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

    private void installCopyShortcut()
    {
        String actionKey = "copyVisibleRows";
        table.getActionMap().put(actionKey, new AbstractAction()
        {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event)
            {
                copyVisibleRowsToClipboard();
            }
        });
        table.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                actionKey
        );
    }

    private void installTableContextMenu(SendActions sendActions)
    {
        table.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent event)
            {
                maybeShowPopup(event);
            }

            @Override
            public void mouseReleased(MouseEvent event)
            {
                maybeShowPopup(event);
            }

            private void maybeShowPopup(MouseEvent event)
            {
                if (!event.isPopupTrigger())
                {
                    return;
                }

                selectRowForPopup(event);
                showActionsMenu(event.getComponent(), event.getX(), event.getY(), sendActions);
            }
        });
    }

    private void copyVisibleRowsToClipboard()
    {
        TableColumnModel columnModel = table.getColumnModel();
        if (columnModel.getColumnCount() == 0 || table.getRowCount() == 0)
        {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        int[] selectedRows = table.getSelectedRows();
        List<Integer> rowsToCopy = new ArrayList<>();
        if (selectedRows.length == 0)
        {
            for (int viewRow = 0; viewRow < table.getRowCount(); viewRow++)
            {
                rowsToCopy.add(viewRow);
            }
        }
        else
        {
            for (int selectedRow : selectedRows)
            {
                rowsToCopy.add(selectedRow);
            }
        }

        List<RequestEntry> entriesToCopy = new ArrayList<>(rowsToCopy.size());
        for (int viewRow : rowsToCopy)
        {
            int modelRow = table.convertRowIndexToModel(viewRow);
            entriesToCopy.add(tableModel.getEntry(modelRow));
        }

        String clipboardText = ClipboardFormatter.format(getVisibleColumnModelIndices(), entriesToCopy);

        Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(clipboardText), null);
    }

    private void showActionsMenu(Component invoker, int x, int y, SendActions sendActions)
    {
        buildActionsMenu(sendActions).show(invoker, x, y);
    }

    public TrackerConnectionConfig trackerConnectionConfig()
    {
        return trackerConfig;
    }

    public void updateTrackerConfigFields(TrackerConnectionConfig config)
    {
        // Tracker config is no longer exposed in the UI.
    }

    private JPopupMenu buildActionsMenu(SendActions sendActions)
    {
        boolean hasSelection = table.getSelectedRowCount() > 0;
        JPopupMenu menu = new JPopupMenu();
        JMenu sendToBurpMenu = new JMenu("Send To Burp");
        sendToBurpMenu.setEnabled(hasSelection);
        sendToBurpMenu.add(createSendMenuItem("Repeater", sendActions.repeaterAction(), hasSelection));
        sendToBurpMenu.add(createSendMenuItem("Intruder", sendActions.intruderAction(), hasSelection));
        sendToBurpMenu.add(createSendMenuItem("Organizer", sendActions.organizerAction(), hasSelection));
        sendToBurpMenu.addSeparator();
        sendToBurpMenu.add(createSendMenuItem("Active Scan", sendActions.activeScanAction(), hasSelection));
        sendToBurpMenu.add(createSendMenuItem("Passive Scan", sendActions.passiveScanAction(), hasSelection));
        sendToBurpMenu.addSeparator();
        sendToBurpMenu.add(createSendMenuItem("Decoder", sendActions.decoderAction(), hasSelection));
        sendToBurpMenu.add(createSendMenuItem("Comparer", sendActions.comparerAction(), hasSelection));
        menu.add(sendToBurpMenu);
        menu.addSeparator();
        menu.add(createSendMenuItem("Export for AuthMatrix...", sendActions.exportAuthMatrixAction(), hasSelection));
        return menu;
    }

    private JMenuItem createSendMenuItem(String label, Runnable action, boolean enabled)
    {
        JMenuItem item = new JMenuItem(label);
        item.setEnabled(enabled);
        item.addActionListener(event -> action.run());
        return item;
    }

    private void selectRowForPopup(MouseEvent event)
    {
        int viewRow = table.rowAtPoint(event.getPoint());
        if (viewRow < 0 || table.isRowSelected(viewRow))
        {
            return;
        }

        table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
    }

    private void showColumnMenu(Component invoker)
    {
        JPopupMenu menu = new JPopupMenu();

        for (int modelIndex : columnOrder)
        {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(
                    tableModel.getColumnName(modelIndex),
                    isColumnVisible(modelIndex)
            );
            item.addActionListener(event -> toggleColumnVisibility(modelIndex, item.isSelected()));
            menu.add(item);
        }

        menu.show(invoker, 0, invoker.getHeight());
    }

    private void toggleColumnVisibility(int modelIndex, boolean visible)
    {
        if (visible)
        {
            showColumn(modelIndex);
        }
        else
        {
            hideColumn(modelIndex);
        }

        visibleColumnsChanged.accept(getVisibleColumnModelIndices());
    }

    private void hideColumn(int modelIndex)
    {
        TableColumnModel columnModel = table.getColumnModel();
        if (columnModel.getColumnCount() <= 1)
        {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        int viewIndex = findVisibleColumnIndex(modelIndex);
        if (viewIndex < 0)
        {
            return;
        }

        columnModel.removeColumn(columnModel.getColumn(viewIndex));
    }

    private void showColumn(int modelIndex)
    {
        if (isColumnVisible(modelIndex))
        {
            return;
        }

        TableColumn column = allColumns.get(modelIndex);
        if (column == null)
        {
            return;
        }

        table.addColumn(column);
        restoreColumnOrder();
    }

    private boolean isColumnVisible(int modelIndex)
    {
        return findVisibleColumnIndex(modelIndex) >= 0;
    }

    private int findVisibleColumnIndex(int modelIndex)
    {
        TableColumnModel columnModel = table.getColumnModel();
        for (int viewIndex = 0; viewIndex < columnModel.getColumnCount(); viewIndex++)
        {
            if (columnModel.getColumn(viewIndex).getModelIndex() == modelIndex)
            {
                return viewIndex;
            }
        }

        return -1;
    }

    private void restoreColumnOrder()
    {
        TableColumnModel columnModel = table.getColumnModel();
        int targetIndex = 0;

        for (int modelIndex : columnOrder)
        {
            int currentIndex = findVisibleColumnIndex(modelIndex);
            if (currentIndex < 0)
            {
                continue;
            }

            if (currentIndex != targetIndex)
            {
                columnModel.moveColumn(currentIndex, targetIndex);
            }
            targetIndex++;
        }
    }

    private void applyVisibleColumns(List<Integer> visibleColumns, boolean notify)
    {
        LinkedHashSet<Integer> visibleSet = new LinkedHashSet<>(visibleColumns);
        if (visibleSet.isEmpty())
        {
            visibleSet.addAll(columnOrder);
        }

        for (int modelIndex : columnOrder)
        {
            if (visibleSet.contains(modelIndex))
            {
                showColumn(modelIndex);
            }
            else
            {
                hideColumn(modelIndex);
            }
        }

        restoreColumnOrder();

        if (notify)
        {
            visibleColumnsChanged.accept(getVisibleColumnModelIndices());
        }
    }

    private List<Integer> getVisibleColumnModelIndices()
    {
        TableColumnModel columnModel = table.getColumnModel();
        List<Integer> visibleColumns = new ArrayList<>(columnModel.getColumnCount());

        for (int viewIndex = 0; viewIndex < columnModel.getColumnCount(); viewIndex++)
        {
            visibleColumns.add(columnModel.getColumn(viewIndex).getModelIndex());
        }

        return visibleColumns;
    }

    private void updateCaptureToggleText(AbstractButton button, boolean enabled)
    {
        button.setText(enabled ? "Capturing" : "Paused");
    }
}
