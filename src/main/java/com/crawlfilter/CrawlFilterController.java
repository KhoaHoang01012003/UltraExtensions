package com.crawlfilter;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import burp.api.montoya.proxy.http.InterceptedRequest;
import burp.api.montoya.proxy.http.ProxyRequestHandler;
import burp.api.montoya.proxy.http.ProxyRequestReceivedAction;
import burp.api.montoya.proxy.http.ProxyRequestToBeSentAction;
import burp.api.montoya.scanner.AuditConfiguration;
import burp.api.montoya.scanner.BuiltInAuditConfiguration;
import burp.api.montoya.scanner.audit.Audit;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class CrawlFilterController implements ProxyRequestHandler
{
    private static final List<String> LEGACY_DEFAULT_IGNORED_SUFFIXES = List.of(
            ".js",
            ".gif",
            ".jpg",
            ".png",
            ".css",
            ".json",
            ".map",
            ".svg"
    );
    private static final List<String> DEFAULT_IGNORED_SUFFIXES = List.of(
            ".js",
            ".gif",
            ".jpg",
            ".png",
            ".css",
            ".json",
            ".map",
            ".svg",
            ".xlsx"
    );
    private static final DateTimeFormatter HISTORY_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MontoyaApi api;
    private final Logging logging;
    private final CrawlFilterPersistence persistence;
    private final CrawlFilterPanel panel;
    private final Set<RequestFingerprint> seenFingerprints = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean captureEnabled = new AtomicBoolean(true);
    private final AtomicBoolean includeHostInFingerprint = new AtomicBoolean(false);
    private final AtomicBoolean staticFilterEnabled = new AtomicBoolean(true);
    private final AtomicReference<List<String>> ignoredSuffixes =
            new AtomicReference<>(DEFAULT_IGNORED_SUFFIXES);
    private final AtomicLong totalSeen = new AtomicLong();
    private final AtomicLong uniqueLogged = new AtomicLong();
    private final AtomicLong duplicatesFiltered = new AtomicLong();
    private final AtomicLong ignoredStaticRequests = new AtomicLong();
    private final AtomicLong sequence = new AtomicLong();

    public CrawlFilterController(MontoyaApi api)
    {
        this.api = api;
        this.logging = api.logging();
        this.persistence = new CrawlFilterPersistence(api.persistence());

        CrawlFilterSettings loadedSettings = persistence.loadSettings(formatSuffixes(DEFAULT_IGNORED_SUFFIXES));
        captureEnabled.set(loadedSettings.captureEnabled());
        includeHostInFingerprint.set(loadedSettings.includeHostInFingerprint());
        staticFilterEnabled.set(loadedSettings.staticFilterEnabled());

        List<String> normalizedSuffixes = normalizeSuffixes(loadedSettings.staticSuffixes());
        List<String> migratedSuffixes = migrateLegacyIgnoredSuffixes(normalizedSuffixes);
        ignoredSuffixes.set(migratedSuffixes);
        CrawlFilterSettings settings = new CrawlFilterSettings(
                loadedSettings.captureEnabled(),
                loadedSettings.includeHostInFingerprint(),
                loadedSettings.staticFilterEnabled(),
                formatSuffixes(migratedSuffixes),
                loadedSettings.visibleColumnModelIndices()
        );
        if (!migratedSuffixes.equals(normalizedSuffixes))
        {
            persistence.saveStaticSuffixes(formatSuffixes(migratedSuffixes));
            logging.logToOutput("Crawl Filter updated the default static suffix list to include .xlsx.");
        }

        this.panel = new CrawlFilterPanel(
                api,
                settings,
                this::clear,
                this::clearRepeaterTabs,
                new CrawlFilterPanel.SendActions(
                        this::sendSelectedToRepeater,
                        this::sendSelectedToIntruder,
                        this::sendSelectedToActiveScan,
                        this::sendSelectedToPassiveScan,
                        this::sendSelectedToDecoder,
                        this::sendSelectedToComparer,
                        this::sendSelectedToOrganizer,
                        this::exportSelectedForAuthMatrix
                ),
                this::setCaptureEnabled,
                this::setIncludeHostInFingerprint,
                this::setStaticFilterEnabled,
                this::setIgnoredSuffixes,
                this::setVisibleColumns
        );

        restoreInitialState();
        refreshStats();
    }

    public Component uiComponent()
    {
        return panel;
    }

    @Override
    public ProxyRequestReceivedAction handleRequestReceived(InterceptedRequest interceptedRequest)
    {
        return ProxyRequestReceivedAction.continueWith(interceptedRequest);
    }

    @Override
    public ProxyRequestToBeSentAction handleRequestToBeSent(InterceptedRequest interceptedRequest)
    {
        try
        {
            if (!captureEnabled.get() || !interceptedRequest.isInScope())
            {
                return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
            }

            processRequest(interceptedRequest, null);
        }
        catch (Exception exception)
        {
            logging.logToError("Crawl Filter failed to inspect a proxy request.", exception);
        }

        return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
    }

    private void restoreInitialState()
    {
        List<ProxyHttpRequestResponse> historyItems = inScopeProxyHistory();

        if (!historyItems.isEmpty())
        {
            restoreFromProxyHistory(historyItems);
            return;
        }

        restorePersistedState();
    }

    private void restoreFromProxyHistory(List<ProxyHttpRequestResponse> historyItems)
    {
        restoreFromProxyHistory(historyItems, true);
    }

    private void restoreFromProxyHistory(List<ProxyHttpRequestResponse> historyItems, boolean logImport)
    {
        resetInMemoryState();
        List<RequestEntry> restoredEntries = new ArrayList<>();

        for (ProxyHttpRequestResponse historyItem : historyItems)
        {
            try
            {
                String timestamp = historyItem.time().toLocalDateTime().format(HISTORY_TIME_FORMATTER);
                processRequest(historyItem.finalRequest(), timestamp, restoredEntries, false);
            }
            catch (Exception exception)
            {
                logging.logToError("Crawl Filter failed to import an item from Proxy history.", exception);
            }
        }

        persistence.clearLogAndStats();
        for (RequestEntry entry : restoredEntries)
        {
            persistence.appendEntry(entry);
        }
        saveStats();

        applyEntriesToUi(restoredEntries);
        if (logImport)
        {
            logging.logToOutput("Crawl Filter imported " + restoredEntries.size() + " unique requests from Proxy history.");
        }
    }

    private void restorePersistedState()
    {
        resetInMemoryState();
        List<RequestEntry> restoredEntries = persistence.loadEntries();

        for (RequestEntry entry : restoredEntries)
        {
            HttpRequest request = entry.request();
            RequestFingerprint fingerprint = RequestFingerprint.from(
                    request.httpService(),
                    entry.method(),
                    entry.path(),
                    includeHostInFingerprint.get()
            );
            seenFingerprints.add(fingerprint);
        }

        uniqueLogged.set(restoredEntries.size());
        sequence.set(restoredEntries.size());
        totalSeen.set(persistence.loadTotalSeen(uniqueLogged.get()));
        duplicatesFiltered.set(persistence.loadDuplicatesFiltered());
        ignoredStaticRequests.set(persistence.loadIgnoredStaticRequests());

        applyEntriesToUi(restoredEntries);
    }

    private void processRequest(HttpRequest request, String persistedTimestamp)
    {
        List<RequestEntry> newEntries = new ArrayList<>(1);
        processRequest(request, persistedTimestamp, newEntries, true);

        if (!newEntries.isEmpty())
        {
            RequestEntry entry = newEntries.get(0);
            runOnEdt(() ->
            {
                panel.addEntry(entry);
                panel.scrollToLatest();
                panel.updateStats(
                        totalSeen.get(),
                        uniqueLogged.get(),
                        duplicatesFiltered.get(),
                        ignoredStaticRequests.get(),
                        captureEnabled.get(),
                        includeHostInFingerprint.get()
                );
            });
            return;
        }

        refreshStats();
    }

    private void processRequest(
            HttpRequest request,
            String persistedTimestamp,
            List<RequestEntry> collectedEntries,
            boolean persistNewEntry
    )
    {
        String method = RequestFingerprint.normalizeMethod(request.method());
        if ("CONNECT".equals(method))
        {
            return;
        }

        String path = RequestFingerprint.normalizePath(request.pathWithoutQuery());
        totalSeen.incrementAndGet();

        if (isIgnoredStaticResource(path))
        {
            ignoredStaticRequests.incrementAndGet();
            saveStats();
            return;
        }

        RequestFingerprint fingerprint = RequestFingerprint.from(
                request.httpService(),
                method,
                path,
                includeHostInFingerprint.get()
        );

        if (!seenFingerprints.add(fingerprint))
        {
            duplicatesFiltered.incrementAndGet();
            saveStats();
            return;
        }

        String timestamp = persistedTimestamp != null
                ? persistedTimestamp
                : HISTORY_TIME_FORMATTER.format(java.time.LocalDateTime.now());
        RequestEntry entry = RequestEntry.from(sequence.incrementAndGet(), request, timestamp);
        uniqueLogged.incrementAndGet();
        collectedEntries.add(entry);

        if (persistNewEntry)
        {
            persistence.appendEntry(entry);
            saveStats();
        }
    }

    private void sendSelectedToRepeater()
    {
        List<RequestEntry> selectedEntries = selectedEntriesFor("Repeater");
        if (selectedEntries.isEmpty())
        {
            return;
        }

        for (RequestEntry entry : selectedEntries)
        {
            api.repeater().sendToRepeater(entry.request(), buildToolLabel(entry));
        }

        logging.logToOutput("Crawl Filter sent " + selectedEntries.size() + " request(s) to Repeater.");
    }

    private void sendSelectedToIntruder()
    {
        List<RequestEntry> selectedEntries = selectedEntriesFor("Intruder");
        if (selectedEntries.isEmpty())
        {
            return;
        }

        for (RequestEntry entry : selectedEntries)
        {
            api.intruder().sendToIntruder(entry.request(), buildToolLabel(entry));
        }

        logging.logToOutput("Crawl Filter sent " + selectedEntries.size() + " request(s) to Intruder.");
    }

    private void sendSelectedToActiveScan()
    {
        List<RequestEntry> selectedEntries = selectedEntriesFor("Active Scan");
        if (selectedEntries.isEmpty())
        {
            return;
        }

        try
        {
            Audit audit = api.scanner().startAudit(
                    AuditConfiguration.auditConfiguration(BuiltInAuditConfiguration.LEGACY_ACTIVE_AUDIT_CHECKS)
            );

            for (RequestEntry entry : selectedEntries)
            {
                audit.addRequest(entry.request());
            }

            logging.logToOutput("Crawl Filter sent " + selectedEntries.size() + " request(s) to Active Scan.");
        }
        catch (Exception exception)
        {
            logging.logToError(
                    "Crawl Filter failed to send requests to Active Scan. This requires Burp Suite Professional.",
                    exception
            );
        }
    }

    private void sendSelectedToPassiveScan()
    {
        List<RequestEntry> selectedEntries = selectedEntriesFor("Passive Scan");
        if (selectedEntries.isEmpty())
        {
            return;
        }

        try
        {
            Audit audit = api.scanner().startAudit(
                    AuditConfiguration.auditConfiguration(BuiltInAuditConfiguration.LEGACY_PASSIVE_AUDIT_CHECKS)
            );

            for (RequestEntry entry : selectedEntries)
            {
                audit.addRequest(entry.request());
            }

            logging.logToOutput("Crawl Filter sent " + selectedEntries.size() + " request(s) to Passive Scan.");
        }
        catch (Exception exception)
        {
            logging.logToError(
                    "Crawl Filter failed to send requests to Passive Scan. This requires Burp Suite Professional.",
                    exception
            );
        }
    }

    private void sendSelectedToDecoder()
    {
        List<RequestEntry> selectedEntries = selectedEntriesFor("Decoder");
        if (selectedEntries.isEmpty())
        {
            return;
        }

        for (RequestEntry entry : selectedEntries)
        {
            api.decoder().sendToDecoder(entry.request().toByteArray());
        }

        logging.logToOutput("Crawl Filter sent " + selectedEntries.size() + " request(s) to Decoder.");
    }

    private void sendSelectedToComparer()
    {
        List<RequestEntry> selectedEntries = selectedEntriesFor("Comparer");
        if (selectedEntries.isEmpty())
        {
            return;
        }

        ByteArray[] requests = selectedEntries.stream()
                .map(entry -> entry.request().toByteArray())
                .toArray(ByteArray[]::new);

        api.comparer().sendToComparer(requests);
        logging.logToOutput("Crawl Filter sent " + selectedEntries.size() + " request(s) to Comparer.");
    }

    private void sendSelectedToOrganizer()
    {
        List<RequestEntry> selectedEntries = selectedEntriesFor("Organizer");
        if (selectedEntries.isEmpty())
        {
            return;
        }

        for (RequestEntry entry : selectedEntries)
        {
            api.organizer().sendToOrganizer(entry.request());
        }

        logging.logToOutput("Crawl Filter sent " + selectedEntries.size() + " request(s) to Organizer.");
    }

    private void exportSelectedForAuthMatrix()
    {
        List<RequestEntry> selectedEntries = selectedEntriesFor("Export for AuthMatrix");
        if (selectedEntries.isEmpty())
        {
            return;
        }

        List<RequestEntry> sortedEntries = selectedEntries.stream()
                .sorted(Comparator.comparingLong(RequestEntry::index))
                .toList();

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export selected requests for AuthMatrix");
        chooser.setFileFilter(new FileNameExtensionFilter("AuthMatrix state (*.json)", "json"));
        chooser.setSelectedFile(new java.io.File(defaultAuthMatrixFilename()));

        int choice = chooser.showSaveDialog(panel);
        if (choice != JFileChooser.APPROVE_OPTION)
        {
            logging.logToOutput("Crawl Filter AuthMatrix export canceled.");
            return;
        }

        Path exportPath = ensureJsonExtension(chooser.getSelectedFile().toPath());
        if (Files.exists(exportPath) && !confirmOverwrite(exportPath))
        {
            logging.logToOutput("Crawl Filter AuthMatrix export canceled.");
            return;
        }

        try
        {
            String stateJson = AuthMatrixStateExporter.buildStateJson(sortedEntries);
            Files.writeString(exportPath, stateJson, StandardCharsets.UTF_8);
            logging.logToOutput(
                    "Crawl Filter exported " + sortedEntries.size() + " request(s) for AuthMatrix to " + exportPath + "."
            );
        }
        catch (IOException exception)
        {
            logging.logToError("Crawl Filter failed to export an AuthMatrix state file.", exception);
        }
    }

    private void clearRepeaterTabs()
    {
        int confirmation = JOptionPane.showConfirmDialog(
                panel,
                "Close all open Repeater tabs using a UI workaround?\nThis is not an official Burp API action.",
                "Clear Repeater Tabs",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirmation != JOptionPane.YES_OPTION)
        {
            logging.logToOutput("Crawl Filter canceled the Repeater clear action.");
            return;
        }

        try
        {
            RepeaterTabCloser.CloseResult result = RepeaterTabCloser.closeAllOpenRepeaterTabs();
            if (result.closedTabs() > 0)
            {
                logging.logToOutput(
                        "Crawl Filter closed " + result.closedTabs() + " Repeater tab(s) across "
                                + result.panesVisited() + " pane(s) using a UI workaround."
                );
                return;
            }

            logging.logToOutput(
                    "Crawl Filter did not find any closable Repeater tabs. Burp's current UI layout may not match this workaround."
            );
        }
        catch (Exception exception)
        {
            logging.logToError("Crawl Filter failed while trying to close Repeater tabs via UI automation.", exception);
        }
    }

    private void setCaptureEnabled(boolean enabled)
    {
        captureEnabled.set(enabled);
        persistence.saveCaptureEnabled(enabled);
        refreshStats();
        logging.logToOutput(enabled ? "Crawl Filter capture resumed." : "Crawl Filter capture paused.");
    }

    private void setIncludeHostInFingerprint(boolean includeHost)
    {
        if (includeHostInFingerprint.getAndSet(includeHost) == includeHost)
        {
            refreshStats();
            return;
        }

        persistence.saveIncludeHostInFingerprint(includeHost);
        clear();
        logging.logToOutput(includeHost
                ? "Crawl Filter dedupe mode changed to host + method + path."
                : "Crawl Filter dedupe mode changed to method + path.");
    }

    private void setStaticFilterEnabled(boolean enabled)
    {
        staticFilterEnabled.set(enabled);
        persistence.saveStaticFilterEnabled(enabled);
        rebuildEntriesFromAvailableSource(enabled
                ? "Crawl Filter static suffix filter enabled."
                : "Crawl Filter static suffix filter disabled.");
    }

    private String setIgnoredSuffixes(String suffixText)
    {
        List<String> normalizedSuffixes = normalizeSuffixes(suffixText);
        String formattedSuffixes = formatSuffixes(normalizedSuffixes);
        ignoredSuffixes.set(normalizedSuffixes);
        persistence.saveStaticSuffixes(formattedSuffixes);

        rebuildEntriesFromAvailableSource(normalizedSuffixes.isEmpty()
                ? "Crawl Filter static suffix list cleared."
                : "Crawl Filter static suffix list set to: " + formattedSuffixes);

        return formattedSuffixes;
    }

    private void setVisibleColumns(List<Integer> visibleColumnModelIndices)
    {
        persistence.saveVisibleColumns(visibleColumnModelIndices);
    }

    private void clear()
    {
        resetInMemoryState();
        persistence.clearLogAndStats();

        runOnEdt(() ->
        {
            panel.clearEntries();
            panel.updateStats(
                    totalSeen.get(),
                    uniqueLogged.get(),
                    duplicatesFiltered.get(),
                    ignoredStaticRequests.get(),
                    captureEnabled.get(),
                    includeHostInFingerprint.get()
            );
        });
    }

    private void resetInMemoryState()
    {
        seenFingerprints.clear();
        totalSeen.set(0);
        uniqueLogged.set(0);
        duplicatesFiltered.set(0);
        ignoredStaticRequests.set(0);
        sequence.set(0);
    }

    private void applyEntriesToUi(List<RequestEntry> entries)
    {
        runOnEdt(() ->
        {
            panel.restoreEntries(entries);
            panel.updateStats(
                    totalSeen.get(),
                    uniqueLogged.get(),
                    duplicatesFiltered.get(),
                    ignoredStaticRequests.get(),
                    captureEnabled.get(),
                    includeHostInFingerprint.get()
            );
        });
    }

    private void refreshStats()
    {
        runOnEdt(() -> panel.updateStats(
                totalSeen.get(),
                uniqueLogged.get(),
                duplicatesFiltered.get(),
                ignoredStaticRequests.get(),
                captureEnabled.get(),
                includeHostInFingerprint.get()
        ));
    }

    private void saveStats()
    {
        persistence.saveStats(
                totalSeen.get(),
                duplicatesFiltered.get(),
                ignoredStaticRequests.get()
        );
    }

    private boolean isIgnoredStaticResource(String path)
    {
        if (!staticFilterEnabled.get())
        {
            return false;
        }

        String normalizedPath = path.toLowerCase(Locale.ROOT);
        for (String suffix : ignoredSuffixes.get())
        {
            if (normalizedPath.endsWith(suffix))
            {
                return true;
            }
        }

        return false;
    }

    private String buildToolLabel(RequestEntry entry)
    {
        return Long.toString(entry.index());
    }

    private List<RequestEntry> selectedEntriesFor(String toolName)
    {
        List<RequestEntry> selectedEntries = panel.selectedEntries();
        if (selectedEntries.isEmpty())
        {
            logging.logToOutput("Crawl Filter: select at least one request to send to " + toolName + ".");
        }

        return selectedEntries;
    }

    private void rebuildEntriesFromAvailableSource(String reason)
    {
        List<ProxyHttpRequestResponse> historyItems = inScopeProxyHistory();
        if (!historyItems.isEmpty())
        {
            restoreFromProxyHistory(historyItems, false);
            logging.logToOutput(reason + " Reapplied to the current Proxy history.");
            return;
        }

        restoreFromRequests(persistence.loadEntries());
        logging.logToOutput(reason + " Reapplied using the persisted Crawl Filter log.");
    }

    private void restoreFromRequests(List<RequestEntry> sourceEntries)
    {
        resetInMemoryState();
        List<RequestEntry> restoredEntries = new ArrayList<>();

        for (RequestEntry entry : sourceEntries)
        {
            try
            {
                processRequest(entry.request(), entry.firstSeenAt(), restoredEntries, false);
            }
            catch (Exception exception)
            {
                logging.logToError("Crawl Filter failed to rebuild an entry while reapplying filters.", exception);
            }
        }

        persistence.clearLogAndStats();
        for (RequestEntry entry : restoredEntries)
        {
            persistence.appendEntry(entry);
        }
        saveStats();
        applyEntriesToUi(restoredEntries);
    }

    private boolean confirmOverwrite(Path exportPath)
    {
        int result = JOptionPane.showConfirmDialog(
                panel,
                "Overwrite existing file?\n" + exportPath,
                "Confirm AuthMatrix Export",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        return result == JOptionPane.YES_OPTION;
    }

    private static Path ensureJsonExtension(Path path)
    {
        String filename = path.getFileName().toString();
        if (filename.toLowerCase(Locale.ROOT).endsWith(".json"))
        {
            return path;
        }

        return path.resolveSibling(filename + ".json");
    }

    private static String defaultAuthMatrixFilename()
    {
        return "authmatrix-requests-" + java.time.LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json";
    }

    private static List<String> normalizeSuffixes(String suffixText)
    {
        if (suffixText == null || suffixText.isBlank())
        {
            return List.of();
        }

        String[] rawTokens = suffixText.split("[,\\r\\n]+");
        LinkedHashSet<String> normalizedSuffixes = new LinkedHashSet<>();

        for (String rawToken : rawTokens)
        {
            String token = rawToken.trim().toLowerCase(Locale.ROOT);
            if (token.isEmpty())
            {
                continue;
            }

            if (!token.startsWith("."))
            {
                token = "." + token;
            }

            normalizedSuffixes.add(token);
        }

        return new ArrayList<>(normalizedSuffixes);
    }

    private static String formatSuffixes(List<String> suffixes)
    {
        return String.join(", ", suffixes);
    }

    private static List<String> migrateLegacyIgnoredSuffixes(List<String> suffixes)
    {
        if (!suffixes.equals(LEGACY_DEFAULT_IGNORED_SUFFIXES) || suffixes.contains(".xlsx"))
        {
            return suffixes;
        }

        List<String> migratedSuffixes = new ArrayList<>(suffixes);
        migratedSuffixes.add(".xlsx");
        return migratedSuffixes;
    }

    private List<ProxyHttpRequestResponse> inScopeProxyHistory()
    {
        return api.proxy().history(
                requestResponse -> requestResponse.finalRequest() != null && requestResponse.finalRequest().isInScope()
        );
    }

    private void runOnEdt(Runnable task)
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            task.run();
            return;
        }

        SwingUtilities.invokeLater(task);
    }
}
