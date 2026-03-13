package com.crawlfilter;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.proxy.http.InterceptedRequest;
import burp.api.montoya.proxy.http.ProxyRequestHandler;
import burp.api.montoya.proxy.http.ProxyRequestReceivedAction;
import burp.api.montoya.proxy.http.ProxyRequestToBeSentAction;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class CrawlFilterController implements ProxyRequestHandler
{
    private static final Set<String> IGNORED_SUFFIXES = Set.of(
            ".js",
            ".gif",
            ".jpg",
            ".png",
            ".css",
            ".json",
            ".map",
            ".svg"
    );

    private final Logging logging;
    private final CrawlFilterPanel panel;
    private final Set<RequestFingerprint> seenFingerprints = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean captureEnabled = new AtomicBoolean(true);
    private final AtomicBoolean includeHostInFingerprint = new AtomicBoolean(false);
    private final AtomicLong totalSeen = new AtomicLong();
    private final AtomicLong uniqueLogged = new AtomicLong();
    private final AtomicLong duplicatesFiltered = new AtomicLong();
    private final AtomicLong ignoredStaticRequests = new AtomicLong();
    private final AtomicLong sequence = new AtomicLong();

    public CrawlFilterController(MontoyaApi api)
    {
        this.logging = api.logging();
        this.panel = new CrawlFilterPanel(
                api,
                this::clear,
                this::setCaptureEnabled,
                this::setIncludeHostInFingerprint
        );

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

            String method = RequestFingerprint.normalizeMethod(interceptedRequest.method());
            if ("CONNECT".equals(method))
            {
                return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
            }

            String path = RequestFingerprint.normalizePath(interceptedRequest.pathWithoutQuery());
            totalSeen.incrementAndGet();

            if (isIgnoredStaticResource(path))
            {
                ignoredStaticRequests.incrementAndGet();
                refreshStats();
                return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
            }

            RequestFingerprint fingerprint = RequestFingerprint.from(
                    interceptedRequest.httpService(),
                    method,
                    path,
                    includeHostInFingerprint.get()
            );

            if (!seenFingerprints.add(fingerprint))
            {
                duplicatesFiltered.incrementAndGet();
                refreshStats();
                return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
            }

            RequestEntry entry = RequestEntry.from(sequence.incrementAndGet(), interceptedRequest, method, path);
            uniqueLogged.incrementAndGet();

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
        }
        catch (Exception exception)
        {
            logging.logToError("Crawl Filter failed to inspect a proxy request.", exception);
        }

        return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
    }

    private void setCaptureEnabled(boolean enabled)
    {
        captureEnabled.set(enabled);
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

        clear();
        logging.logToOutput(includeHost
                ? "Crawl Filter dedupe mode changed to host + method + path."
                : "Crawl Filter dedupe mode changed to method + path.");
    }

    private void clear()
    {
        seenFingerprints.clear();
        totalSeen.set(0);
        uniqueLogged.set(0);
        duplicatesFiltered.set(0);
        ignoredStaticRequests.set(0);
        sequence.set(0);

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

    private boolean isIgnoredStaticResource(String path)
    {
        String normalizedPath = path.toLowerCase(Locale.ROOT);
        for (String suffix : IGNORED_SUFFIXES)
        {
            if (normalizedPath.endsWith(suffix))
            {
                return true;
            }
        }

        return false;
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
