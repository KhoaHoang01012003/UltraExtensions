package com.crawlfilter;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public final class RequestTableModel extends AbstractTableModel
{
    public static final int PATH_COLUMN_INDEX = 6;
    public static final int QUERY_COLUMN_INDEX = 7;

    private static final String[] COLUMNS = {
            "#",
            "First Seen",
            "Method",
            "Scheme",
            "Host",
            "Port",
            "Path",
            "Query",
            "Full URL"
    };

    private final List<RequestEntry> entries = new ArrayList<>();

    public static int columnCount()
    {
        return COLUMNS.length;
    }

    public static boolean isValidColumnModelIndex(int modelIndex)
    {
        return modelIndex >= 0 && modelIndex < COLUMNS.length;
    }

    public static List<Integer> defaultVisibleColumnModelIndices()
    {
        List<Integer> visibleColumns = new ArrayList<>(COLUMNS.length);
        for (int modelIndex = 0; modelIndex < COLUMNS.length; modelIndex++)
        {
            visibleColumns.add(modelIndex);
        }

        return visibleColumns;
    }

    @Override
    public int getRowCount()
    {
        return entries.size();
    }

    @Override
    public int getColumnCount()
    {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column)
    {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex)
    {
        return switch (columnIndex)
                {
                    case 0 -> Long.class;
                    case 5 -> Integer.class;
                    default -> String.class;
                };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex)
    {
        RequestEntry entry = entries.get(rowIndex);

        return switch (columnIndex)
                {
                    case 0 -> entry.index();
                    case 1 -> entry.firstSeenAt();
                    case 2 -> entry.method();
                    case 3 -> entry.scheme();
                    case 4 -> entry.host();
                    case 5 -> entry.port();
                    case 6 -> entry.path();
                    case 7 -> entry.query();
                    case 8 -> entry.url();
                    default -> "";
                };
    }

    public void addEntry(RequestEntry entry)
    {
        int row = entries.size();
        entries.add(entry);
        fireTableRowsInserted(row, row);
    }

    public void setEntries(List<RequestEntry> restoredEntries)
    {
        entries.clear();
        entries.addAll(restoredEntries);
        fireTableDataChanged();
    }

    public void clearEntries()
    {
        entries.clear();
        fireTableDataChanged();
    }

    public RequestEntry getEntry(int rowIndex)
    {
        return entries.get(rowIndex);
    }
}
