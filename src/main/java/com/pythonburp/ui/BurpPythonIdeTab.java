package com.pythonburp.ui;

import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.catalog.PackageCatalog;
import com.pythonburp.catalog.PackageCatalogLoader;
import com.pythonburp.concurrency.Edt;
import com.pythonburp.concurrency.IdeExecutors;
import com.pythonburp.concurrency.RuntimeActivityCoordinator;
import com.pythonburp.console.ConsoleEvent;
import com.pythonburp.console.ConsoleEventType;
import com.pythonburp.python.CPythonRuntimeFactory;
import com.pythonburp.python.ScriptExecutor;
import com.pythonburp.python.ScriptExecutionMode;
import com.pythonburp.python.ScriptRunRequest;
import com.pythonburp.python.ScriptRunResult;
import com.pythonburp.python.ScriptStatus;
import com.pythonburp.packages.EmbeddedPipRunner;
import com.pythonburp.packages.PackageInventoryReader;
import com.pythonburp.packages.PackageManagerService;
import com.pythonburp.packages.PackageRequestStore;
import com.pythonburp.packages.PackageSettingsStore;
import com.pythonburp.packages.SharedPackageEnvironment;
import com.pythonburp.storage.ExtensionDataCleaner;
import com.pythonburp.storage.ExtensionDataPaths;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

public final class BurpPythonIdeTab extends JPanel {
    private final EditorPanel editor = new EditorPanel();
    private final ConsolePanel console = new ConsolePanel();
    private final StatusBar statusBar = new StatusBar();
    private final ScriptExecutor scriptExecutor;
    private final IdeExecutors executors;
    private final BurpBridge bridge;
    private final JComboBox<ScriptExecutionMode> executionMode = new JComboBox<>(ScriptExecutionMode.values());
    private final JTextField customCommand = new JTextField(28);
    private final JLabel customCommandLabel = new JLabel("Python Args");
    private final JToggleButton interactiveMode = new JToggleButton("Interactive");
    private volatile boolean interactiveEnabled;
    private Future<ScriptRunResult> activeRun;

    public BurpPythonIdeTab(IdeExecutors executors, BurpBridge bridge) {
        this(executors, bridge, defaults());
    }

    private BurpPythonIdeTab(IdeExecutors executors, BurpBridge bridge, Defaults defaults) {
        this(executors, bridge, defaults.paths(), defaults.coordinator(), defaults.runtimeFactory(), defaults.packageService());
    }

    public BurpPythonIdeTab(IdeExecutors executors, BurpBridge bridge, ExtensionDataPaths paths,
                            RuntimeActivityCoordinator coordinator, CPythonRuntimeFactory runtimeFactory,
                            PackageManagerService packageService) {
        super(new BorderLayout());
        this.executors = executors;
        this.bridge = bridge;
        this.scriptExecutor = new ScriptExecutor(executors, () -> runtimeFactory.get(bridge, this::requestInteractiveInput), coordinator);

        JButton load = new JButton("Load");
        JButton saveAs = new JButton("Save As");
        JButton run = new JButton("Run");
        JButton stop = new JButton("Stop");
        JButton clearLog = new JButton("Clear Log");
        JButton help = new JButton("Help");
        load.addActionListener(event -> loadScript());
        saveAs.addActionListener(event -> saveScriptAs());
        run.addActionListener(event -> runScript());
        stop.addActionListener(event -> stopScript());
        clearLog.addActionListener(event -> console.clear());
        help.addActionListener(event -> HelpDialogs.showPythonIdeHelp(this));
        executionMode.addActionListener(event -> updateExecutionModeUi());
        interactiveMode.addActionListener(event -> {
            interactiveEnabled = interactiveMode.isSelected();
            console.appendSystem("Interactive mode " + (interactiveEnabled ? "enabled" : "disabled"));
        });

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(load);
        toolbar.add(saveAs);
        toolbar.addSeparator();
        toolbar.add(run);
        toolbar.add(stop);
        toolbar.addSeparator();
        toolbar.add(interactiveMode);
        toolbar.add(clearLog);
        toolbar.add(help);

        JPanel modeBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        modeBar.add(new JLabel("Mode"));
        modeBar.add(executionMode);
        modeBar.add(customCommandLabel);
        modeBar.add(customCommand);
        JPanel header = new JPanel(new BorderLayout());
        header.add(toolbar, BorderLayout.NORTH);
        header.add(modeBar, BorderLayout.SOUTH);

        JSplitPane main = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editor, console);
        main.setResizeWeight(0.7);

        JPanel editorWorkspace = new JPanel(new BorderLayout());
        editorWorkspace.add(header, BorderLayout.NORTH);
        editorWorkspace.add(main, BorderLayout.CENTER);
        editorWorkspace.add(statusBar, BorderLayout.SOUTH);

        PackageManagerPanel packagePanel = new PackageManagerPanel();
        PackageManagerController packageController = new PackageManagerController(
            packagePanel, packageService, executors, paths.root(), coordinator);

        JTabbedPane workspaces = new JTabbedPane();
        workspaces.addTab("Editor", editorWorkspace);
        workspaces.addTab("Package Manager", packagePanel);
        add(workspaces, BorderLayout.CENTER);
        updateExecutionModeUi();
        SwingUtilities.invokeLater(packageController::refresh);
    }

    private static Defaults defaults() {
        ExtensionDataPaths paths = ExtensionDataPaths.windowsDefault();
        RuntimeActivityCoordinator coordinator = new RuntimeActivityCoordinator();
        CPythonRuntimeFactory runtimeFactory = new CPythonRuntimeFactory(paths);
        PackageCatalog catalog;
        try { catalog = PackageCatalogLoader.loadBundled(); }
        catch (IOException e) { catalog = new PackageCatalog(List.of()); }
        ExtensionDataCleaner cleaner = new ExtensionDataCleaner(paths, runtimeFactory.userPackages());
        PackageManagerService service = new PackageManagerService(
            paths, coordinator, new SharedPackageEnvironment(paths, runtimeFactory.userPackages()),
            new PackageRequestStore(paths.packageRequests()),
            new PackageSettingsStore(paths.settings().resolve("pip.properties")),
            new PackageInventoryReader(catalog), cleaner, new EmbeddedPipRunner(runtimeFactory::pipEnvironmentOverrides),
            runtimeFactory.userPackages(), catalog, true, runtimeFactory.pipAvailable(), runtimeFactory::pythonExecutable
        );
        return new Defaults(paths, coordinator, runtimeFactory, service);
    }

    private record Defaults(ExtensionDataPaths paths, RuntimeActivityCoordinator coordinator,
                            CPythonRuntimeFactory runtimeFactory, PackageManagerService packageService) {}

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(editor::focusEditor);
    }

    private void loadScript() {
        Edt.requireEdt();
        JFileChooser chooser = pythonFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        try {
            editor.setSource(ScriptFileService.load(path));
            statusBar.setStatus("Loaded " + path.getFileName());
            console.appendSystem("Loaded " + path);
        } catch (IOException e) {
            statusBar.setStatus("Load failed");
            console.append(List.of(ConsoleEvent.now(ConsoleEventType.STDERR, "Load failed: " + e.getMessage())));
        }
    }

    private void saveScriptAs() {
        Edt.requireEdt();
        JFileChooser chooser = pythonFileChooser();
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        try {
            ScriptFileService.save(path, editor.source());
            statusBar.setStatus("Saved " + path.getFileName());
            console.appendSystem("Saved " + path);
        } catch (IOException e) {
            statusBar.setStatus("Save failed");
            console.append(List.of(ConsoleEvent.now(ConsoleEventType.STDERR, "Save failed: " + e.getMessage())));
        }
    }

    private JFileChooser pythonFileChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Python scripts (*.py)", "py"));
        return chooser;
    }

    private void runScript() {
        Edt.requireEdt();
        if (activeRun != null && !activeRun.isDone()) {
            statusBar.setStatus("Already running");
            return;
        }
        ScriptRunRequest request;
        try {
            request = currentRequest();
        } catch (IllegalArgumentException e) {
            statusBar.setStatus("Invalid command");
            console.append(List.of(ConsoleEvent.now(ConsoleEventType.STDERR, e.getMessage())));
            return;
        }
        statusBar.setStatus("Running");
        console.appendSystem(request.mode() == ScriptExecutionMode.CUSTOM_COMMAND
            ? "Running custom command: python.exe " + request.commandTail()
            : "Running editor script");
        Future<ScriptRunResult> run;
        try {
            run = scriptExecutor.run(request);
        } catch (IllegalStateException e) {
            statusBar.setStatus("Package operation active");
            console.append(List.of(ConsoleEvent.now(ConsoleEventType.STDERR, e.getMessage())));
            return;
        }
        activeRun = run;
        Thread waiter = new Thread(() -> {
            try {
                ScriptRunResult result = run.get();
                Edt.runLater(() -> publishResult(run, result));
            } catch (CancellationException e) {
                Edt.runLater(() -> publishCancelled(run));
            } catch (Exception e) {
                Edt.runLater(() -> publishFailure(run, e));
            }
        }, "burp-python-ui-result-waiter");
        waiter.setDaemon(true);
        waiter.start();
    }

    private void stopScript() {
        Edt.requireEdt();
        if (activeRun != null && !activeRun.isDone()) {
            activeRun.cancel(true);
            console.cancelPendingInput("Interactive input canceled because the script was stopped.");
            statusBar.setStatus("Stopping");
        }
    }

    private String requestInteractiveInput(String prompt) throws IOException, InterruptedException {
        if (!interactiveEnabled) {
            throw new IOException("Script requested interactive input. Enable Interactive mode and run again.");
        }
        return console.requestInput(prompt);
    }

    private void updateExecutionModeUi() {
        boolean custom = executionMode.getSelectedItem() == ScriptExecutionMode.CUSTOM_COMMAND;
        customCommand.setEnabled(custom);
        customCommandLabel.setEnabled(custom);
        editor.setEditorEnabled(!custom);
    }

    private ScriptRunRequest currentRequest() {
        ScriptExecutionMode mode = (ScriptExecutionMode) executionMode.getSelectedItem();
        if (mode == ScriptExecutionMode.CUSTOM_COMMAND) {
            String tail = customCommand.getText();
            if (tail == null || tail.isBlank()) {
                throw new IllegalArgumentException("Enter the command tail after python.exe, for example: -m abc -h xyz");
            }
            return ScriptRunRequest.customCommand(tail, Duration.ofMinutes(5));
        }
        return ScriptRunRequest.editorScript(editor.source(), Duration.ofMinutes(5));
    }

    private void publishFailure(Future<ScriptRunResult> run, Exception e) {
        Edt.requireEdt();
        if (activeRun == run) {
            activeRun = null;
            statusBar.setStatus("Failed");
            console.append(List.of(ConsoleEvent.now(ConsoleEventType.STDERR, e.toString())));
        }
    }

    private void publishCancelled(Future<ScriptRunResult> run) {
        Edt.requireEdt();
        if (activeRun == run) {
            activeRun = null;
            statusBar.setStatus("Ready");
            console.appendSystem("Script stopped");
        }
    }

    private void publishResult(Future<ScriptRunResult> run, ScriptRunResult result) {
        Edt.requireEdt();
        if (activeRun == run) {
            activeRun = null;
            if (!result.stdout().isBlank()) {
                console.append(List.of(ConsoleEvent.now(ConsoleEventType.STDOUT, result.stdout())));
            }
            if (!result.stderr().isBlank()) {
                console.append(List.of(ConsoleEvent.now(ConsoleEventType.STDERR, result.stderr())));
            }
            if (!result.errorMessage().isBlank()) {
                console.append(List.of(ConsoleEvent.now(ConsoleEventType.STDERR, result.errorMessage())));
            }
            statusBar.setStatus(result.status() == ScriptStatus.SUCCEEDED ? "Ready" : "Failed");
        }
    }
}
