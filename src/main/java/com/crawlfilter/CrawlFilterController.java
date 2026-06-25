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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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
    private static final DateTimeFormatter ACTION_LOG_TIME_FORMATTER =
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
                loadedSettings.visibleColumnModelIndices(),
                loadedSettings.trackerHost(),
                loadedSettings.trackerRoundId(),
                loadedSettings.trackerJwtToken()
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

    private TrackerConnectionConfig saveTrackerConnectionConfig(TrackerConnectionConfig rawConfig)
    {
        TrackerConnectionConfig normalizedConfig = TrackerConnectionConfig.normalize(
                rawConfig.baseUrl(),
                rawConfig.roundId(),
                rawConfig.jwtToken()
        );
        persistence.saveTrackerConnectionConfig(normalizedConfig);
        logging.logToOutput(
                normalizedConfig.isComplete()
                        ? "Crawl Filter saved API config for " + normalizedConfig.baseUrl()
                        + " round " + normalizedConfig.roundId() + "."
                        : "Crawl Filter saved the API config fields."
        );
        return normalizedConfig;
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
            logging.logToOutput("Crawl Filter: select at least one request to use " + toolName + ".");
        }

        return selectedEntries;
    }

    private void addTestcasesToSelectedApis()
    {
        List<RequestEntry> selectedEntries = selectedEntriesFor("Add Testcase");
        if (selectedEntries.isEmpty())
        {
            return;
        }

        TrackingSystemClient client = trackingClientOrNull();
        if (client == null)
        {
            return;
        }

        appendActionResult("Add Testcase", "STARTED", "-", "Loading APIs and testcase templates for " + selectedEntries.size() + " selected request(s)");
        runAsync("Add Testcase", () ->
        {
            try
            {
                Map<RequestEntry, TrackingSystemClient.TrackedApi> matchedApis =
                        matchSelectedEntriesToTrackedApis(selectedEntries, client, "Add Testcase");
                if (matchedApis.isEmpty())
                {
                    appendActionResult("Add Testcase", "INFO", "-", "No selected requests matched any API in the tracking system");
                    return;
                }

                List<TrackingSystemClient.VulnerabilityTemplateOption> templates = client.listVulnerabilityTemplates();
                if (templates.isEmpty())
                {
                    showInfoDialog(
                            "Add Testcase",
                            "No vulnerability templates were returned by the tracking system."
                    );
                    appendActionResult("Add Testcase", "INFO", "-", "No vulnerability templates were returned");
                    return;
                }
                List<TrackingSystemClient.VulnerabilityTemplateOption> selectedTemplates = supplyOnEdt(() -> chooseMultiple(
                        "Add Testcase",
                        "Choose testcase templates to add to " + matchedApis.size() + " API(s).",
                        templates
                ));
                if (selectedTemplates.isEmpty())
                {
                    logging.logToOutput("Crawl Filter canceled Add Testcase.");
                    return;
                }

                int successCount = 0;
                List<String> failures = new ArrayList<>();
                for (Map.Entry<RequestEntry, TrackingSystemClient.TrackedApi> matchedApi : matchedApis.entrySet())
                {
                    for (TrackingSystemClient.VulnerabilityTemplateOption template : selectedTemplates)
                    {
                        try
                        {
                            client.addTestcase(matchedApi.getValue().id(), template.id());
                            appendActionResult(
                                    "Add Testcase",
                                    "SUCCESS",
                                    matchedApi.getKey().url(),
                                    "Added template " + template.name() + " [" + template.id() + "]"
                            );
                            successCount++;
                        }
                        catch (IOException exception)
                        {
                            appendActionResult(
                                    "Add Testcase",
                                    "FAILED",
                                    matchedApi.getKey().url(),
                                    template.name() + " -> " + exception.getMessage()
                            );
                            failures.add(matchedApi.getKey().url() + " -> " + template.name() + ": " + exception.getMessage());
                        }
                    }
                }

                showOperationSummary(
                        "Add Testcase",
                        "Added " + successCount + " testcase assignment(s) across " + matchedApis.size() + " API(s).",
                        failures
                );
            }
            catch (Exception exception)
            {
                logActionFailure("Add Testcase", exception);
            }
        });
    }

    private void markSelectedTestcasesDone()
    {
        List<RequestEntry> selectedEntries = selectedEntriesFor("Mark Testcase as Done");
        if (selectedEntries.isEmpty())
        {
            return;
        }

        TrackingSystemClient client = trackingClientOrNull();
        if (client == null)
        {
            return;
        }

        appendActionResult(
                "Mark Testcase as Done",
                "STARTED",
                "-",
                "Loading APIs and testcase list for " + selectedEntries.size() + " selected request(s)"
        );
        runAsync("Mark Testcase as Done", () ->
        {
            try
            {
                Map<RequestEntry, TrackingSystemClient.TrackedApi> matchedApis =
                        matchSelectedEntriesToTrackedApis(selectedEntries, client, "Mark Testcase as Done");
                if (matchedApis.isEmpty())
                {
                    appendActionResult("Mark Testcase as Done", "INFO", "-", "No selected requests matched any API in the tracking system");
                    return;
                }

                Map<String, String> testcaseNames = new LinkedHashMap<>();
                Map<String, List<TrackingSystemClient.ApiTestcaseOption>> testcasesByApiId = new LinkedHashMap<>();
                for (TrackingSystemClient.TrackedApi trackedApi : matchedApis.values())
                {
                    List<TrackingSystemClient.ApiTestcaseOption> testcases = client.listApiTestcases(trackedApi.id());
                    testcasesByApiId.put(trackedApi.id(), testcases);
                    for (TrackingSystemClient.ApiTestcaseOption testcase : testcases)
                    {
                        testcaseNames.putIfAbsent(testcase.name(), testcase.name());
                    }
                }

                if (testcaseNames.isEmpty())
                {
                    showInfoDialog(
                            "Mark Testcase as Done",
                            "No testcases were found for the selected API(s)."
                    );
                    appendActionResult("Mark Testcase as Done", "INFO", "-", "No existing testcases were found on the selected APIs");
                    return;
                }

                List<String> selectedNames = supplyOnEdt(() -> chooseMultiple(
                        "Mark Testcase as Done",
                        "Choose testcase names to mark as Done for " + matchedApis.size() + " API(s).",
                        new ArrayList<>(testcaseNames.values())
                ));
                if (selectedNames.isEmpty())
                {
                    logging.logToOutput("Crawl Filter canceled Mark Testcase as Done.");
                    return;
                }

                Set<String> selectedNameSet = Set.copyOf(selectedNames);
                int successCount = 0;
                List<String> failures = new ArrayList<>();
                for (Map.Entry<RequestEntry, TrackingSystemClient.TrackedApi> matchedApi : matchedApis.entrySet())
                {
                    List<TrackingSystemClient.ApiTestcaseOption> testcases =
                            testcasesByApiId.getOrDefault(matchedApi.getValue().id(), List.of());
                    for (TrackingSystemClient.ApiTestcaseOption testcase : testcases)
                    {
                        if (!selectedNameSet.contains(testcase.name()))
                        {
                            continue;
                        }

                        try
                        {
                            client.markTestcaseDone(matchedApi.getValue().id(), testcase);
                            appendActionResult(
                                    "Mark Testcase as Done",
                                    "SUCCESS",
                                    matchedApi.getKey().url(),
                                    "Marked testcase " + testcase.name() + " [" + testcase.id() + "] as Done"
                            );
                            successCount++;
                        }
                        catch (IOException exception)
                        {
                            appendActionResult(
                                    "Mark Testcase as Done",
                                    "FAILED",
                                    matchedApi.getKey().url(),
                                    testcase.name() + " -> " + exception.getMessage()
                            );
                            failures.add(matchedApi.getKey().url() + " -> " + testcase.name() + ": " + exception.getMessage());
                        }
                    }
                }

                showOperationSummary(
                        "Mark Testcase as Done",
                        "Marked " + successCount + " testcase instance(s) as Done.",
                        failures
                );
            }
            catch (Exception exception)
            {
                logActionFailure("Mark Testcase as Done", exception);
            }
        });
    }

    private void markSelectedApisDone()
    {
        List<RequestEntry> selectedEntries = selectedEntriesFor("Mark API as Done");
        if (selectedEntries.isEmpty())
        {
            return;
        }

        TrackingSystemClient client = trackingClientOrNull();
        if (client == null)
        {
            return;
        }

        int confirmation = JOptionPane.showConfirmDialog(
                panel,
                "Mark " + selectedEntries.size() + " selected API(s) as Done?",
                "Mark API as Done",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (confirmation != JOptionPane.YES_OPTION)
        {
            logging.logToOutput("Crawl Filter canceled Mark API as Done.");
            return;
        }

        appendActionResult("Mark API as Done", "STARTED", "-", "Loading APIs for " + selectedEntries.size() + " selected request(s)");
        runAsync("Mark API as Done", () ->
        {
            try
            {
                Map<RequestEntry, TrackingSystemClient.TrackedApi> matchedApis =
                        matchSelectedEntriesToTrackedApis(selectedEntries, client, "Mark API as Done");
                if (matchedApis.isEmpty())
                {
                    appendActionResult("Mark API as Done", "INFO", "-", "No selected requests matched any API in the tracking system");
                    return;
                }

                int successCount = 0;
                List<String> failures = new ArrayList<>();
                for (Map.Entry<RequestEntry, TrackingSystemClient.TrackedApi> matchedApi : matchedApis.entrySet())
                {
                    try
                    {
                        client.markApiDone(matchedApi.getValue().id());
                        appendActionResult(
                                "Mark API as Done",
                                "SUCCESS",
                                matchedApi.getKey().url(),
                                "Marked API as Done"
                        );
                        successCount++;
                    }
                    catch (IOException exception)
                    {
                        appendActionResult(
                                "Mark API as Done",
                                "FAILED",
                                matchedApi.getKey().url(),
                                exception.getMessage()
                        );
                        failures.add(matchedApi.getKey().url() + ": " + exception.getMessage());
                    }
                }

                showOperationSummary(
                        "Mark API as Done",
                        "Marked " + successCount + " API(s) as Done.",
                        failures
                );
            }
            catch (Exception exception)
            {
                logActionFailure("Mark API as Done", exception);
            }
        });
    }

    private void renameSelectedApis()
    {
        List<RequestEntry> selectedEntries = selectedEntriesFor("Rename APIs");
        if (selectedEntries.isEmpty())
        {
            return;
        }

        TrackingSystemClient client = trackingClientOrNull();
        if (client == null)
        {
            return;
        }

        appendActionResult("Rename APIs", "STARTED", "-", "Loading APIs for " + selectedEntries.size() + " selected request(s)");
        runAsync("Rename APIs", () ->
        {
            try
            {
                Map<RequestEntry, TrackingSystemClient.TrackedApi> matchedApis =
                        matchSelectedEntriesToTrackedApis(selectedEntries, client, "Rename APIs");
                if (matchedApis.isEmpty())
                {
                    appendActionResult("Rename APIs", "INFO", "-", "No selected requests matched any API in the tracking system");
                    return;
                }

                String mappingText = supplyOnEdt(() -> promptForTextBlock(
                        "Rename APIs",
                        "Paste one mapping per line using:\n[full_URL] >>> [name]",
                        buildRenameTemplate(selectedEntries)
                ));
                if (mappingText == null)
                {
                    logging.logToOutput("Crawl Filter canceled Rename APIs.");
                    return;
                }

                Map<String, String> renameMap = parseRenameMappings(mappingText);
                if (renameMap.isEmpty())
                {
                    runOnEdt(() -> JOptionPane.showMessageDialog(
                            panel,
                            "No valid rename mappings were found.\nUse: [full_URL] >>> [name]",
                            "Rename APIs",
                            JOptionPane.WARNING_MESSAGE
                    ));
                    return;
                }

                int successCount = 0;
                List<String> failures = new ArrayList<>();
                for (Map.Entry<RequestEntry, TrackingSystemClient.TrackedApi> matchedApi : matchedApis.entrySet())
                {
                    String newName = renameMap.get(normalizeComparableUrl(matchedApi.getKey().url()));
                    if (newName == null || newName.isBlank())
                    {
                        continue;
                    }

                    try
                    {
                        client.renameApi(matchedApi.getValue().id(), newName);
                        appendActionResult(
                                "Rename APIs",
                                "SUCCESS",
                                matchedApi.getKey().url(),
                                "Renamed to " + newName
                        );
                        successCount++;
                    }
                    catch (IOException exception)
                    {
                        appendActionResult(
                                "Rename APIs",
                                "FAILED",
                                matchedApi.getKey().url(),
                                exception.getMessage()
                        );
                        failures.add(matchedApi.getKey().url() + ": " + exception.getMessage());
                    }
                }

                showOperationSummary(
                        "Rename APIs",
                        "Renamed " + successCount + " API(s).",
                        failures
                );
            }
            catch (Exception exception)
            {
                logActionFailure("Rename APIs", exception);
            }
        });
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

    private TrackingSystemClient trackingClientOrNull()
    {
        try
        {
            TrackerConnectionConfig config = TrackerConnectionConfig.normalize(
                    panel.trackerConnectionConfig().baseUrl(),
                    panel.trackerConnectionConfig().roundId(),
                    panel.trackerConnectionConfig().jwtToken()
            );
            panel.updateTrackerConfigFields(config);
            persistence.saveTrackerConnectionConfig(config);

            if (!config.isComplete())
            {
                JOptionPane.showMessageDialog(
                        panel,
                        "Please enter API Host, Round ID, and JWT before using this action.",
                        "Missing API Config",
                        JOptionPane.WARNING_MESSAGE
                );
                return null;
            }

            return new TrackingSystemClient(
                    api,
                    config,
                    message -> appendActionResult("HTTP Trace", "TRACE", config.baseUrl(), message)
            );
        }
        catch (IllegalArgumentException exception)
        {
            JOptionPane.showMessageDialog(
                    panel,
                    exception.getMessage(),
                    "Invalid API Config",
                    JOptionPane.ERROR_MESSAGE
            );
            logging.logToOutput("Crawl Filter rejected the API config: " + exception.getMessage());
            return null;
        }
    }

    private Map<RequestEntry, TrackingSystemClient.TrackedApi> matchSelectedEntriesToTrackedApis(
            List<RequestEntry> selectedEntries,
            TrackingSystemClient client,
            String actionName
    ) throws IOException
    {
        List<TrackingSystemClient.TrackedApi> trackedApis = client.listApis();
        Map<String, TrackingSystemClient.TrackedApi> trackedByUrl = new LinkedHashMap<>();
        for (TrackingSystemClient.TrackedApi trackedApi : trackedApis)
        {
            trackedByUrl.putIfAbsent(normalizeComparableUrl(trackedApi.url()), trackedApi);
        }

        Map<RequestEntry, TrackingSystemClient.TrackedApi> matchedApis = new LinkedHashMap<>();
        List<String> missingUrls = new ArrayList<>();
        for (RequestEntry entry : selectedEntries)
        {
            TrackingSystemClient.TrackedApi trackedApi = trackedByUrl.get(normalizeComparableUrl(entry.url()));
            if (trackedApi == null)
            {
                missingUrls.add(entry.url());
                continue;
            }

            matchedApis.put(entry, trackedApi);
        }

        if (!missingUrls.isEmpty())
        {
            showWarningDialog(
                    actionName,
                    actionName + " could not match " + missingUrls.size()
                            + " selected URL(s) in the tracking system.\nFirst unmatched URL:\n" + missingUrls.get(0)
            );
            logging.logToOutput(
                    "Crawl Filter could not match " + missingUrls.size() + " selected URL(s) in the tracking system."
            );
        }

        return matchedApis;
    }

    private <T> List<T> chooseMultiple(String title, String message, List<T> options)
    {
        if (options == null || options.isEmpty())
        {
            JOptionPane.showMessageDialog(
                    panel,
                    "No selectable items were returned by the tracking system.",
                    title,
                    JOptionPane.INFORMATION_MESSAGE
            );
            return List.of();
        }

        DefaultListModel<T> model = new DefaultListModel<>();
        for (T option : options)
        {
            model.addElement(option);
        }

        JList<T> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setVisibleRowCount(Math.min(18, Math.max(8, options.size())));

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setPreferredSize(new Dimension(560, 320));

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(new JLabel(message), BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(
                panel,
                content,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION)
        {
            return List.of();
        }

        return list.getSelectedValuesList();
    }

    private String promptForTextBlock(String title, String message, String initialValue)
    {
        JTextArea textArea = new JTextArea(initialValue, 16, 80);
        textArea.setLineWrap(false);
        textArea.setWrapStyleWord(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(720, 360));

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(new JLabel("<html>" + message.replace("\n", "<br>") + "</html>"), BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(
                panel,
                content,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION)
        {
            return null;
        }

        return textArea.getText();
    }

    private String buildRenameTemplate(List<RequestEntry> selectedEntries)
    {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < selectedEntries.size(); index++)
        {
            if (index > 0)
            {
                builder.append(System.lineSeparator());
            }
            builder.append(selectedEntries.get(index).url()).append(" >>> ");
        }
        return builder.toString();
    }

    private Map<String, String> parseRenameMappings(String mappingText)
    {
        Map<String, String> mappings = new LinkedHashMap<>();
        String[] lines = mappingText.split("\\R");
        for (String rawLine : lines)
        {
            String line = rawLine.trim();
            if (line.isEmpty())
            {
                continue;
            }

            int separatorIndex = line.indexOf(">>>");
            if (separatorIndex < 0)
            {
                continue;
            }

            String url = line.substring(0, separatorIndex).trim();
            String name = line.substring(separatorIndex + 3).trim();
            if (url.isEmpty() || name.isEmpty())
            {
                continue;
            }

            mappings.put(normalizeComparableUrl(url), name);
        }
        return mappings;
    }

    private void showOperationSummary(String title, String successMessage, List<String> failures)
    {
        if (failures.isEmpty())
        {
            appendActionResult(title, "SUMMARY", "-", successMessage);
            logging.logToOutput("Crawl Filter " + successMessage);
            runOnEdt(() -> JOptionPane.showMessageDialog(panel, successMessage, title, JOptionPane.INFORMATION_MESSAGE));
            return;
        }

        StringBuilder builder = new StringBuilder(successMessage)
                .append("\n")
                .append("Failures: ")
                .append(failures.size());
        int previewCount = Math.min(5, failures.size());
        for (int index = 0; index < previewCount; index++)
        {
            builder.append("\n- ").append(failures.get(index));
        }

        if (failures.size() > previewCount)
        {
            builder.append("\n...");
        }

        appendActionResult(title, "SUMMARY", "-", successMessage + " Failures: " + failures.size());
        logging.logToOutput("Crawl Filter " + successMessage + " Failures: " + failures.size() + ".");
        runOnEdt(() -> JOptionPane.showMessageDialog(panel, builder.toString(), title, JOptionPane.WARNING_MESSAGE));
    }

    private void logActionFailure(String actionName, Exception exception)
    {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        appendActionResult(actionName, "ERROR", "-", message);
        logging.logToError("Crawl Filter failed to execute " + actionName + ".", exception);
        showErrorDialog(actionName, actionName + " failed.\n" + message);
    }

    private static String normalizeComparableUrl(String rawUrl)
    {
        if (rawUrl == null)
        {
            return "";
        }

        String trimmed = rawUrl.trim();
        if (trimmed.isEmpty())
        {
            return "";
        }

        try
        {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            String path = uri.getRawPath();
            if (path == null || path.isBlank())
            {
                path = "/";
            }

            String query = uri.getRawQuery();
            boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
            StringBuilder builder = new StringBuilder();
            if (!scheme.isBlank())
            {
                builder.append(scheme).append("://");
            }
            builder.append(host);
            if (port >= 0 && !defaultPort)
            {
                builder.append(':').append(port);
            }
            builder.append(path);
            if (query != null && !query.isBlank())
            {
                builder.append('?').append(query);
            }
            return builder.toString();
        }
        catch (URISyntaxException ignored)
        {
            return trimmed.toLowerCase(Locale.ROOT);
        }
    }

    private void appendActionResult(String actionName, String status, String target, String detail)
    {
        String timestamp = java.time.LocalDateTime.now().format(ACTION_LOG_TIME_FORMATTER);
        String sanitizedTarget = target == null || target.isBlank() ? "-" : target;
        String sanitizedDetail = detail == null ? "" : detail.replace('\r', ' ').replace('\n', ' ').trim();
        runOnEdt(() -> panel.appendResultLog(
                "[" + timestamp + "] "
                        + actionName
                        + " | "
                        + status
                        + " | "
                        + sanitizedTarget
                        + " | "
                        + sanitizedDetail
        ));
    }

    private void runAsync(String actionName, Runnable task)
    {
        Thread worker = new Thread(() ->
        {
            try
            {
                task.run();
            }
            catch (Exception exception)
            {
                logActionFailure(actionName, exception);
            }
        }, "crawl-filter-" + actionName.toLowerCase(Locale.ROOT).replace(' ', '-'));
        worker.setDaemon(true);
        worker.start();
    }

    private <T> T supplyOnEdt(Supplier<T> supplier)
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            return supplier.get();
        }

        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<RuntimeException> runtimeFailure = new AtomicReference<>();
        AtomicReference<Exception> checkedFailure = new AtomicReference<>();
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    result.set(supplier.get());
                }
                catch (RuntimeException exception)
                {
                    runtimeFailure.set(exception);
                }
            });
        }
        catch (Exception exception)
        {
            checkedFailure.set(exception);
        }

        if (runtimeFailure.get() != null)
        {
            throw runtimeFailure.get();
        }
        if (checkedFailure.get() != null)
        {
            throw new IllegalStateException("Failed to execute a UI task on the Swing event dispatch thread.", checkedFailure.get());
        }

        return result.get();
    }

    private void showInfoDialog(String title, String message)
    {
        supplyOnEdt(() ->
        {
            JOptionPane.showMessageDialog(panel, message, title, JOptionPane.INFORMATION_MESSAGE);
            return null;
        });
    }

    private void showWarningDialog(String title, String message)
    {
        supplyOnEdt(() ->
        {
            JOptionPane.showMessageDialog(panel, message, title, JOptionPane.WARNING_MESSAGE);
            return null;
        });
    }

    private void showErrorDialog(String title, String message)
    {
        supplyOnEdt(() ->
        {
            JOptionPane.showMessageDialog(panel, message, title, JOptionPane.ERROR_MESSAGE);
            return null;
        });
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
