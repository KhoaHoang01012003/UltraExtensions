package com.crawlfilter;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public final class RequestTableModel extends AbstractTableModel
{
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
