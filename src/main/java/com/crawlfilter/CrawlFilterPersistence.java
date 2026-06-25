package com.crawlfilter;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.persistence.Persistence;
import burp.api.montoya.persistence.PersistedList;
import burp.api.montoya.persistence.PersistedObject;
import burp.api.montoya.persistence.Preferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class CrawlFilterPersistence
{
    private static final String KEY_CAPTURE_ENABLED = "crawlFilter.captureEnabled";
    private static final String KEY_INCLUDE_HOST = "crawlFilter.includeHostInFingerprint";
    private static final String KEY_STATIC_FILTER_ENABLED = "crawlFilter.staticFilterEnabled";
    private static final String KEY_STATIC_SUFFIXES = "crawlFilter.staticSuffixes";
    private static final String KEY_VISIBLE_COLUMNS = "crawlFilter.visibleColumns";
    private static final String KEY_TRACKER_HOST = "crawlFilter.trackerHost";
    private static final String KEY_TRACKER_ROUND_ID = "crawlFilter.trackerRoundId";
    private static final String KEY_TRACKER_JWT = "crawlFilter.trackerJwtToken";

    private static final String KEY_LOG_REQUESTS = "crawlFilter.logRequests";
    private static final String KEY_LOG_TIMESTAMPS = "crawlFilter.logTimestamps";
    private static final String KEY_TOTAL_SEEN = "crawlFilter.totalSeen";
    private static final String KEY_DUPLICATES = "crawlFilter.duplicatesFiltered";
    private static final String KEY_IGNORED_STATIC = "crawlFilter.ignoredStaticRequests";

    private final Preferences preferences;
    private final PersistedObject extensionData;
    private final PersistedList<HttpRequest> persistedRequests;
    private final PersistedList<String> persistedTimestamps;

    public CrawlFilterPersistence(Persistence persistence)
    {
        this.preferences = persistence.preferences();
        this.extensionData = persistence.extensionData();
        this.persistedRequests = getOrCreateHttpRequestList(KEY_LOG_REQUESTS);
        this.persistedTimestamps = getOrCreateStringList(KEY_LOG_TIMESTAMPS);
    }

    public CrawlFilterSettings loadSettings(String defaultStaticSuffixes)
    {
        return new CrawlFilterSettings(
                readBoolean(preferences.getBoolean(KEY_CAPTURE_ENABLED), true),
                readBoolean(preferences.getBoolean(KEY_INCLUDE_HOST), false),
                readBoolean(preferences.getBoolean(KEY_STATIC_FILTER_ENABLED), true),
                readString(preferences.getString(KEY_STATIC_SUFFIXES), defaultStaticSuffixes),
                parseVisibleColumns(preferences.getString(KEY_VISIBLE_COLUMNS)),
                readString(preferences.getString(KEY_TRACKER_HOST), ""),
                readString(preferences.getString(KEY_TRACKER_ROUND_ID), ""),
                readString(preferences.getString(KEY_TRACKER_JWT), "")
        );
    }

    public List<RequestEntry> loadEntries()
    {
        int entryCount = Math.min(persistedRequests.size(), persistedTimestamps.size());
        List<RequestEntry> entries = new ArrayList<>(entryCount);

        for (int index = 0; index < entryCount; index++)
        {
            entries.add(RequestEntry.from(index + 1L, persistedRequests.get(index), persistedTimestamps.get(index)));
        }

        return entries;
    }

    public long loadTotalSeen(long uniqueLogged)
    {
        return Math.max(uniqueLogged, readLong(extensionData.getLong(KEY_TOTAL_SEEN), uniqueLogged));
    }

    public long loadDuplicatesFiltered()
    {
        return readLong(extensionData.getLong(KEY_DUPLICATES), 0L);
    }

    public long loadIgnoredStaticRequests()
    {
        return readLong(extensionData.getLong(KEY_IGNORED_STATIC), 0L);
    }

    public void appendEntry(RequestEntry entry)
    {
        persistedRequests.add(entry.request());
        persistedTimestamps.add(entry.firstSeenAt());
    }

    public void saveStats(long totalSeen, long duplicatesFiltered, long ignoredStaticRequests)
    {
        extensionData.setLong(KEY_TOTAL_SEEN, totalSeen);
        extensionData.setLong(KEY_DUPLICATES, duplicatesFiltered);
        extensionData.setLong(KEY_IGNORED_STATIC, ignoredStaticRequests);
    }

    public void clearLogAndStats()
    {
        persistedRequests.clear();
        persistedTimestamps.clear();
        saveStats(0L, 0L, 0L);
    }

    public void saveCaptureEnabled(boolean enabled)
    {
        preferences.setBoolean(KEY_CAPTURE_ENABLED, enabled);
    }

    public void saveIncludeHostInFingerprint(boolean includeHostInFingerprint)
    {
        preferences.setBoolean(KEY_INCLUDE_HOST, includeHostInFingerprint);
    }

    public void saveStaticFilterEnabled(boolean enabled)
    {
        preferences.setBoolean(KEY_STATIC_FILTER_ENABLED, enabled);
    }

    public void saveStaticSuffixes(String suffixes)
    {
        preferences.setString(KEY_STATIC_SUFFIXES, suffixes);
    }

    public void saveVisibleColumns(List<Integer> visibleColumnModelIndices)
    {
        preferences.setString(KEY_VISIBLE_COLUMNS, serializeVisibleColumns(visibleColumnModelIndices));
    }

    public void saveTrackerConnectionConfig(TrackerConnectionConfig config)
    {
        preferences.setString(KEY_TRACKER_HOST, config.baseUrl());
        preferences.setString(KEY_TRACKER_ROUND_ID, config.roundId());
        preferences.setString(KEY_TRACKER_JWT, config.jwtToken());
    }

    private PersistedList<HttpRequest> getOrCreateHttpRequestList(String key)
    {
        PersistedList<HttpRequest> list = extensionData.getHttpRequestList(key);
        if (list != null)
        {
            return list;
        }

        PersistedList<HttpRequest> createdList = PersistedList.persistedHttpRequestList();
        extensionData.setHttpRequestList(key, createdList);
        return createdList;
    }

    private PersistedList<String> getOrCreateStringList(String key)
    {
        PersistedList<String> list = extensionData.getStringList(key);
        if (list != null)
        {
            return list;
        }

        PersistedList<String> createdList = PersistedList.persistedStringList();
        extensionData.setStringList(key, createdList);
        return createdList;
    }

    private List<Integer> parseVisibleColumns(String persistedValue)
    {
        if (persistedValue == null || persistedValue.isBlank())
        {
            return RequestTableModel.defaultVisibleColumnModelIndices();
        }

        LinkedHashSet<Integer> visibleColumns = new LinkedHashSet<>();
        String[] rawTokens = persistedValue.split(",");

        for (String rawToken : rawTokens)
        {
            String token = rawToken.trim();
            if (token.isEmpty())
            {
                continue;
            }

            try
            {
                int modelIndex = Integer.parseInt(token);
                if (RequestTableModel.isValidColumnModelIndex(modelIndex))
                {
                    visibleColumns.add(modelIndex);
                }
            }
            catch (NumberFormatException ignored)
            {
                // Ignore malformed persisted values and fall back to the remaining valid ones.
            }
        }

        if (visibleColumns.isEmpty())
        {
            return RequestTableModel.defaultVisibleColumnModelIndices();
        }

        return new ArrayList<>(visibleColumns);
    }

    private String serializeVisibleColumns(List<Integer> visibleColumnModelIndices)
    {
        StringBuilder builder = new StringBuilder();

        for (int index = 0; index < visibleColumnModelIndices.size(); index++)
        {
            if (index > 0)
            {
                builder.append(',');
            }
            builder.append(visibleColumnModelIndices.get(index));
        }

        return builder.toString();
    }

    private static boolean readBoolean(Boolean value, boolean fallback)
    {
        return value != null ? value : fallback;
    }

    private static long readLong(Long value, long fallback)
    {
        return value != null ? value : fallback;
    }

    private static String readString(String value, String fallback)
    {
        return value != null ? value : fallback;
    }
}
