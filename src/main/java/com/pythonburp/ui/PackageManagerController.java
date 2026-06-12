package com.pythonburp.ui;

import com.pythonburp.concurrency.Edt;
import com.pythonburp.concurrency.IdeExecutors;
import com.pythonburp.concurrency.RuntimeActivityCoordinator;
import com.pythonburp.packages.PackageManagerService;
import com.pythonburp.packages.PackageManagerSettings;
import com.pythonburp.packages.PackageOperationResult;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public final class PackageManagerController {
    private final PackageManagerPanel panel;
    private final PackageManagerService service;
    private final IdeExecutors executors;
    private final Path dataRoot;
    private final RuntimeActivityCoordinator coordinator;
    private volatile boolean operationBusy;

    public PackageManagerController(PackageManagerPanel panel, PackageManagerService service,
                                    IdeExecutors executors, Path dataRoot,
                                    RuntimeActivityCoordinator coordinator) {
        this.panel = panel;
        this.service = service;
        this.executors = executors;
        this.dataRoot = dataRoot;
        this.coordinator = coordinator;
        wireActions();
        Timer activityTimer = new Timer(250, event -> updateAvailability());
        activityTimer.start();
    }

    public void refresh() {
        submit("Refreshing packages", () -> new PackageOperationResult(true, "Ready", service.inventory()));
    }

    private void wireActions() {
        panel.install.addActionListener(event -> submit("Installing", () ->
            service.installRequirement(panel.requirement.getText(), this::publishOutput)));
        panel.installWheel.addActionListener(event -> chooseAndInstall("Wheel files (*.whl)", "whl",
            path -> service.installWheel(path, this::publishOutput)));
        panel.installRequirements.addActionListener(event -> chooseAndInstall("Requirements files (*.txt)", "txt",
            path -> service.installRequirements(path, this::publishOutput)));
        panel.refresh.addActionListener(event -> refresh());
        panel.uninstall.addActionListener(event -> {
            String name = panel.selectedPackage();
            if (!name.isBlank()) submit("Uninstalling " + name, () -> service.uninstall(name, this::publishOutput));
        });
        panel.clearPackages.addActionListener(event -> submit("Clearing packages", service::clearUserPackages));
        panel.clearPipCache.addActionListener(event -> submit("Clearing pip cache", service::clearPipCache));
        panel.resetAll.addActionListener(event -> resetAll());
        panel.settings.addActionListener(event -> editSettings());
    }

    private void chooseAndInstall(String description, String extension,
                                  java.util.function.Function<Path, PackageOperationResult> operation) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(description, extension));
        if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
            Path selected = chooser.getSelectedFile().toPath();
            submit("Installing " + selected.getFileName(), () -> operation.apply(selected));
        }
    }

    private void resetAll() {
        int answer = JOptionPane.showConfirmDialog(panel,
            "Delete all Python IDE data under:\n" + dataRoot + "\n\nSaved scripts outside this path are preserved.",
            "Reset All Extension Data", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.OK_OPTION) submit("Resetting extension data", service::resetAllExtensionData);
    }

    private void editSettings() {
        try {
            PackageManagerSettings current = service.settings();
            JTextField index = new JTextField(current.indexUrl());
            JTextField extra = new JTextField(current.extraIndexUrl());
            JPasswordField proxy = new JPasswordField(current.proxyUrl());
            JTextField hosts = new JTextField(String.join(",", current.trustedHosts()));
            JSpinner timeout = new JSpinner(new SpinnerNumberModel(current.timeoutSeconds(), 1, 600, 1));
            JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
            for (Object[] row : List.of(
                new Object[]{"Index URL", index}, new Object[]{"Extra index URL", extra},
                new Object[]{"Proxy URL", proxy}, new Object[]{"Trusted hosts", hosts},
                new Object[]{"Timeout seconds", timeout})) {
                form.add(new JLabel(String.valueOf(row[0])));
                form.add((java.awt.Component) row[1]);
            }
            if (JOptionPane.showConfirmDialog(panel, form, "Package Settings",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                PackageManagerSettings updated = new PackageManagerSettings(index.getText(), extra.getText(),
                    new String(proxy.getPassword()), Arrays.stream(hosts.getText().split(","))
                    .map(String::trim).filter(value -> !value.isBlank()).toList(), (Integer) timeout.getValue());
                submit("Saving settings", () -> {
                    service.saveSettings(updated);
                    return new PackageOperationResult(true, "Settings saved", service.inventory());
                });
            }
        } catch (Exception e) {
            panel.appendOutput("[err] " + e.getMessage());
        }
    }

    private void submit(String status, Operation operation) {
        Edt.requireEdt();
        if (coordinator.snapshot().activeScripts() > 0) {
            panel.appendOutput("[err] Stop running scripts before changing packages");
            return;
        }
        operationBusy = true;
        panel.setBusy(true, status);
        executors.submitPackageTask(() -> {
            PackageOperationResult result;
            try { result = operation.run(); }
            catch (Exception e) { result = new PackageOperationResult(false, e.toString(), List.of()); }
            PackageOperationResult published = result;
            Edt.runLater(() -> {
                panel.setInventory(published.inventory());
                panel.appendOutput((published.succeeded() ? "[system] " : "[err] ") + published.message());
                operationBusy = false;
                updateAvailability();
            });
        });
    }

    private void updateAvailability() {
        boolean scriptsRunning = coordinator.snapshot().activeScripts() > 0;
        boolean disabled = operationBusy || scriptsRunning || service.resetRequired();
        String text = service.resetRequired() ? "Reload extension required"
            : scriptsRunning ? "Stop scripts to manage packages"
            : operationBusy ? "Working" : "Ready";
        panel.setBusy(disabled, text);
    }

    private void publishOutput(String line) {
        Edt.runLater(() -> panel.appendOutput(line));
    }

    @FunctionalInterface
    private interface Operation { PackageOperationResult run() throws Exception; }
}
