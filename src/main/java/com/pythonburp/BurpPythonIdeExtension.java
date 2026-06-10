package com.pythonburp;

import burp.api.montoya.MontoyaApi;
import com.formdev.flatlaf.FlatDarkLaf;
import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.bridge.MontoyaHttpBridge;
import com.pythonburp.catalog.PackageCatalog;
import com.pythonburp.catalog.PackageCatalogLoader;
import com.pythonburp.concurrency.IdeExecutors;
import com.pythonburp.core.ExtensionContext;
import com.pythonburp.core.VersionInfo;
import com.pythonburp.ui.BurpPythonIdeTab;

import java.util.List;

public final class BurpPythonIdeExtension {
    private ExtensionContext context;

    public void initialize(MontoyaApi api) {
        closeContext();
        api.extension().setName(VersionInfo.EXTENSION_NAME);
        FlatDarkLaf.setup();
        ExtensionContext initializedContext = new ExtensionContext(api, new IdeExecutors(defaultScriptThreads()));
        this.context = initializedContext;
        PackageCatalog catalog = loadCatalog(api);
        BurpBridge bridge = new BurpBridge(new MontoyaHttpBridge(api));
        BurpPythonIdeTab tab = new BurpPythonIdeTab(initializedContext.executors(), catalog, bridge);
        api.userInterface().registerSuiteTab("Python IDE", tab);
        api.extension().registerUnloadingHandler(() -> closeContext(initializedContext));
        api.logging().logToOutput(VersionInfo.EXTENSION_NAME + " " + VersionInfo.EXTENSION_VERSION + " loaded");
    }

    private int defaultScriptThreads() {
        int cpus = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(4, cpus - 1));
    }

    private PackageCatalog loadCatalog(MontoyaApi api) {
        try {
            return PackageCatalogLoader.loadBundled();
        } catch (Exception e) {
            api.logging().logToError("Failed to load package catalog: " + e);
            return new PackageCatalog(List.of());
        }
    }

    private void closeContext() {
        ExtensionContext currentContext = context;
        if (currentContext != null) {
            closeContext(currentContext);
        }
    }

    private void closeContext(ExtensionContext contextToClose) {
        contextToClose.close();
        if (context == contextToClose) {
            context = null;
        }
    }
}
