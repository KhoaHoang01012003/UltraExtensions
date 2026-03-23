package com.crawlfilter;

import java.util.List;

public record CrawlFilterSettings(
        boolean captureEnabled,
        boolean includeHostInFingerprint,
        boolean staticFilterEnabled,
        String staticSuffixes,
        List<Integer> visibleColumnModelIndices
)
{
    public CrawlFilterSettings
    {
        if (staticSuffixes == null)
        {
            staticSuffixes = "";
        }

        if (visibleColumnModelIndices == null || visibleColumnModelIndices.isEmpty())
        {
            visibleColumnModelIndices = RequestTableModel.defaultVisibleColumnModelIndices();
        }
        else
        {
            visibleColumnModelIndices = List.copyOf(visibleColumnModelIndices);
        }
    }

    public static CrawlFilterSettings defaults(String defaultStaticSuffixes)
    {
        return new CrawlFilterSettings(
                true,
                false,
                true,
                defaultStaticSuffixes,
                RequestTableModel.defaultVisibleColumnModelIndices()
        );
    }
}

