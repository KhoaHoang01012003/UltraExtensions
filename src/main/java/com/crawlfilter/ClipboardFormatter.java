package com.crawlfilter;

import java.util.List;

final class ClipboardFormatter
{
    private ClipboardFormatter()
    {
    }

    static String format(List<Integer> visibleColumnModelIndices, List<RequestEntry> entries)
    {
        StringBuilder builder = new StringBuilder();
        boolean queryVisible = visibleColumnModelIndices.contains(RequestTableModel.QUERY_COLUMN_INDEX);

        for (int rowIndex = 0; rowIndex < entries.size(); rowIndex++)
        {
            if (rowIndex > 0)
            {
                builder.append(System.lineSeparator());
            }

            appendRow(builder, visibleColumnModelIndices, entries.get(rowIndex), queryVisible);
        }

        return builder.toString();
    }

    private static void appendRow(
            StringBuilder builder,
            List<Integer> visibleColumnModelIndices,
            RequestEntry entry,
            boolean queryVisible
    )
    {
        for (int columnIndex = 0; columnIndex < visibleColumnModelIndices.size(); columnIndex++)
        {
            if (columnIndex > 0)
            {
                builder.append('\t');
            }

            int modelIndex = visibleColumnModelIndices.get(columnIndex);
            builder.append(sanitizeCellValue(valueForColumn(modelIndex, entry, queryVisible)));
        }
    }

    private static Object valueForColumn(int modelIndex, RequestEntry entry, boolean queryVisible)
    {
        return switch (modelIndex)
                {
                    case 0 -> entry.index();
                    case 1 -> entry.firstSeenAt();
                    case 2 -> entry.method();
                    case 3 -> entry.scheme();
                    case 4 -> entry.host();
                    case 5 -> entry.port();
                    case 6 -> queryVisible ? entry.path() : combinePathAndQuery(entry);
                    case 7 -> entry.query();
                    case 8 -> entry.url();
                    default -> "";
                };
    }

    private static String sanitizeCellValue(Object value)
    {
        if (value == null)
        {
            return "";
        }

        return value.toString()
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
    }

    private static String combinePathAndQuery(RequestEntry entry)
    {
        if (entry.query() == null || entry.query().isBlank())
        {
            return entry.path();
        }

        return entry.path() + "?" + entry.query();
    }
}
