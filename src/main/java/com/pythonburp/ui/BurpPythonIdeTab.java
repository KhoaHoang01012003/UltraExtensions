package com.pythonburp.ui;

import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.catalog.PackageCatalog;
import com.pythonburp.concurrency.Edt;
import com.pythonburp.concurrency.IdeExecutors;
import com.pythonburp.console.ConsoleEvent;
import com.pythonburp.console.ConsoleEventType;
import com.pythonburp.python.GraalPyPythonRuntime;
import com.pythonburp.python.ScriptExecutor;
import com.pythonburp.python.ScriptRunRequest;
import com.pythonburp.python.ScriptRunResult;
import com.pythonburp.python.ScriptStatus;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import java.awt.BorderLayout;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Future;

public final class BurpPythonIdeTab extends JPanel {
    private final EditorPanel editor = new EditorPanel();
    private final ConsolePanel console = new ConsolePanel();
    private final StatusBar statusBar = new StatusBar();
    private final ScriptExecutor scriptExecutor;
    private Future<ScriptRunResult> activeRun;

    public BurpPythonIdeTab(IdeExecutors executors, PackageCatalog catalog, BurpBridge bridge) {
        super(new BorderLayout());
        this.scriptExecutor = new ScriptExecutor(executors, () -> new GraalPyPythonRuntime(bridge));

        JButton run = new JButton("Run");
        JButton stop = new JButton("Stop");
        run.addActionListener(event -> runScript());
        stop.addActionListener(event -> stopScript());

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(run);
        toolbar.add(stop);

        JTabbedPane right = new JTabbedPane();
        right.addTab("Packages", new PackageCatalogPanel(catalog));

        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editor, right);
        center.setResizeWeight(0.75);

        JSplitPane main = new JSplitPane(JSplitPane.VERTICAL_SPLIT, center, console);
        main.setResizeWeight(0.7);

        add(toolbar, BorderLayout.NORTH);
        add(main, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
    }

    private void runScript() {
        Edt.requireEdt();
        statusBar.setStatus("Running");
        console.appendSystem("Running script");
        activeRun = scriptExecutor.run(new ScriptRunRequest(editor.source(), Duration.ofMinutes(5)));
        Thread waiter = new Thread(() -> {
            try {
                ScriptRunResult result = activeRun.get();
                Edt.runLater(() -> publishResult(result));
            } catch (Exception e) {
                Edt.runLater(() -> {
                    statusBar.setStatus("Failed");
                    console.append(List.of(ConsoleEvent.now(ConsoleEventType.STDERR, e.toString())));
                });
            }
        }, "burp-python-ui-result-waiter");
        waiter.setDaemon(true);
        waiter.start();
    }

    private void stopScript() {
        Edt.requireEdt();
        if (activeRun != null) {
            activeRun.cancel(true);
            statusBar.setStatus("Stopping");
        }
    }

    private void publishResult(ScriptRunResult result) {
        Edt.requireEdt();
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
