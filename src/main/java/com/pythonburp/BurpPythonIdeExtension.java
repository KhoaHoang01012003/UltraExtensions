package com.pythonburp;

import burp.api.montoya.MontoyaApi;
import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.bridge.MontoyaHttpBridge;
import com.pythonburp.catalog.PackageCatalog;
import com.pythonburp.catalog.PackageCatalogLoader;
import com.pythonburp.concurrency.Edt;
import com.pythonburp.concurrency.IdeExecutors;
import com.pythonburp.concurrency.RuntimeActivityCoordinator;
import com.pythonburp.core.ExtensionContext;
import com.pythonburp.core.VersionInfo;
import com.pythonburp.packages.EmbeddedPipRunner;
import com.pythonburp.packages.PackageInventoryReader;
import com.pythonburp.packages.PackageManagerService;
import com.pythonburp.packages.PackageRequestStore;
import com.pythonburp.packages.PackageSettingsStore;
import com.pythonburp.packages.SharedPackageEnvironment;
import com.pythonburp.python.CPythonRuntimeFactory;
import com.pythonburp.storage.ExtensionDataCleaner;
import com.pythonburp.storage.ExtensionDataPaths;
import com.pythonburp.ui.BurpPythonIdeTab;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class BurpPythonIdeExtension {
    private final Function<ExtensionDataPaths, CPythonRuntimeFactory> runtimeFactoryFactory;
    private ExtensionContext context;

    public BurpPythonIdeExtension() {
        this(CPythonRuntimeFactory::new);
    }

    BurpPythonIdeExtension(Function<ExtensionDataPaths, CPythonRuntimeFactory> runtimeFactoryFactory) {
        this.runtimeFactoryFactory = Objects.requireNonNull(runtimeFactoryFactory, "runtimeFactoryFactory");
    }

    public void initialize(MontoyaApi api) {
        closeContext();
        api.extension().setName(VersionInfo.EXTENSION_NAME);
        ExtensionDataPaths paths = ExtensionDataPaths.windowsDefault();
        CPythonRuntimeFactory runtimeFactory;
        try {
            runtimeFactory = runtimeFactoryFactory.apply(paths);
        } catch (RuntimeException e) {
            api.logging().logToError(e.getMessage() == null ? e.toString() : e.getMessage());
            return;
        }
        ExtensionContext initializedContext = new ExtensionContext(api, new IdeExecutors(defaultScriptThreads()));
        this.context = initializedContext;
        BurpBridge bridge = new BurpBridge(new MontoyaHttpBridge(api));
        RuntimeActivityCoordinator coordinator = new RuntimeActivityCoordinator();
        ExtensionDataCleaner cleaner = new ExtensionDataCleaner(paths, runtimeFactory.userPackages());
        PackageCatalog catalog = loadCatalog(api);
        PackageManagerService packageService = new PackageManagerService(
            paths, coordinator, new SharedPackageEnvironment(paths, runtimeFactory.userPackages()),
            new PackageRequestStore(paths.packageRequests()),
            new PackageSettingsStore(paths.settings().resolve("pip.properties")),
            new PackageInventoryReader(catalog), cleaner, new EmbeddedPipRunner(runtimeFactory::pipEnvironmentOverrides),
            runtimeFactory.userPackages(), catalog, true, runtimeFactory.pipAvailable(), runtimeFactory::pythonExecutable
        );
        Edt.runAndWait(() -> {
            BurpPythonIdeTab tab = new BurpPythonIdeTab(initializedContext.executors(), bridge, paths,
                coordinator, runtimeFactory, packageService);
            api.userInterface().registerSuiteTab("Python IDE", tab);
        });
        initializedContext.executors().submitPackageTask(() -> {
            try {
                List<java.nio.file.Path> remaining = cleaner.cleanupPending();
                if (!remaining.isEmpty()) api.logging().logToError("Pending extension data cleanup: " + remaining);
            } catch (IOException e) {
                api.logging().logToError("Failed to clean pending extension data: " + e);
            }
        });
        api.extension().registerUnloadingHandler(() -> closeContext(initializedContext));
        api.logging().logToOutput(
            "Using Zenmap Python " + runtimeFactory.environment().version() + " at "
                + runtimeFactory.pythonExecutable()
        );
        if (!runtimeFactory.environment().runtimeDetails().isBlank()) {
            api.logging().logToOutput("Zenmap Python fingerprint: " + runtimeFactory.environment().runtimeDetails());
        }
        if (runtimeFactory.compatibilityRoot() != null) {
            api.logging().logToOutput("Using complete Python 3.14.3 compatibility runtime at " + runtimeFactory.compatibilityRoot());
        }
        if (runtimeFactory.usingBundledPipFallback()) {
            api.logging().logToOutput("Using bundled pip compatibility layer from " + paths.root());
            if (runtimeFactory.pipProbeWarning() != null && !runtimeFactory.pipProbeWarning().isBlank()) {
                api.logging().logToOutput(runtimeFactory.pipProbeWarning());
            }
        } else if (!runtimeFactory.pipAvailable()) {
            if (runtimeFactory.pipProbeWarning() != null && !runtimeFactory.pipProbeWarning().isBlank()) {
                api.logging().logToError(runtimeFactory.pipProbeWarning());
            }
            api.logging().logToError(packageService.pipUnavailableMessage());
        }
        api.logging().logToOutput(VersionInfo.EXTENSION_NAME + " " + VersionInfo.EXTENSION_VERSION + " loaded");
    }

    private int defaultScriptThreads() {
        int cpus = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(4, cpus - 1));
    }

    private PackageCatalog loadCatalog(MontoyaApi api) {
        try { return PackageCatalogLoader.loadBundled(); }
        catch (IOException e) {
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
