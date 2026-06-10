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
import java.util.concurrent.CancellationException;
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
        if (activeRun != null && !activeRun.isDone()) {
            statusBar.setStatus("Already running");
            return;
        }
        statusBar.setStatus("Running");
        console.appendSystem("Running script");
        Future<ScriptRunResult> run = scriptExecutor.run(new ScriptRunRequest(editor.source(), Duration.ofMinutes(5)));
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
            statusBar.setStatus("Stopping");
        }
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
