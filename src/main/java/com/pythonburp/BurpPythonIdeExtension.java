package com.pythonburp;

import burp.api.montoya.MontoyaApi;
import com.pythonburp.core.VersionInfo;

public final class BurpPythonIdeExtension {
    public void initialize(MontoyaApi api) {
        api.extension().setName(VersionInfo.EXTENSION_NAME);
        api.logging().logToOutput(VersionInfo.EXTENSION_NAME + " " + VersionInfo.EXTENSION_VERSION + " loaded");
    }
}
