package com.pythonburp;

import burp.api.montoya.MontoyaApi;
import com.pythonburp.concurrency.IdeExecutors;
import com.pythonburp.core.ExtensionContext;
import com.pythonburp.core.VersionInfo;

public final class BurpPythonIdeExtension {
    private ExtensionContext context;

    public void initialize(MontoyaApi api) {
        api.extension().setName(VersionInfo.EXTENSION_NAME);
        this.context = new ExtensionContext(api, new IdeExecutors(defaultScriptThreads()));
        api.logging().logToOutput(VersionInfo.EXTENSION_NAME + " " + VersionInfo.EXTENSION_VERSION + " loaded");
    }

    private int defaultScriptThreads() {
        int cpus = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(4, cpus - 1));
    }
}
