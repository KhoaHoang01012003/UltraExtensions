package com.pythonburp.ui;

import com.pythonburp.concurrency.Edt;
import com.pythonburp.concurrency.IdeExecutors;
import com.pythonburp.repeater.RepeaterBatchResult;
import com.pythonburp.repeater.RepeaterRequestNormalizer;
import com.pythonburp.repeater.RepeaterTabSnapshot;
import com.pythonburp.repeater.RepeaterWorkspaceScanner;

import javax.swing.text.JTextComponent;
import java.util.List;

public final class RepeaterBatchController {
    private final RepeaterBatchPanel panel;
    private final IdeExecutors executors;
    private final RepeaterWorkspaceScanner scanner = new RepeaterWorkspaceScanner();

    public RepeaterBatchController(RepeaterBatchPanel panel, IdeExecutors executors) {
        this.panel = panel;
        this.executors = executors;
        wireActions();
    }

    public void scan() {
        panel.setBusy(true, "Scanning Repeater tabs");
        executors.submitUtilityTask(() -> {
            List<RepeaterTabSnapshot> snapshots = scanner.scanOpenTabs();
            Edt.runLater(() -> {
                panel.setSnapshots(snapshots);
                panel.setBusy(false, "Found " + snapshots.size() + " candidate tabs");
            });
        });
    }

    public void normalizeSelected() {
        List<RepeaterTabSnapshot> selected = panel.selectedSnapshots();
        if (selected.isEmpty()) {
            panel.appendOutput("[system] No selected Repeater tabs to normalize");
            return;
        }
        panel.setBusy(true, "Normalizing selected tabs");
        executors.submitUtilityTask(() -> normalize(selected));
    }

    public void normalizeAll() {
        List<RepeaterTabSnapshot> all = panel.allSnapshots();
        if (all.isEmpty()) {
            panel.appendOutput("[system] No scanned Repeater tabs to normalize");
            return;
        }
        panel.setBusy(true, "Normalizing all scanned tabs");
        executors.submitUtilityTask(() -> normalize(all));
    }

    private RepeaterBatchResult normalize(List<RepeaterTabSnapshot> targets) {
        int modified = 0;
        List<Runnable> uiUpdates = new java.util.ArrayList<>();
        for (RepeaterTabSnapshot target : targets) {
            String original = target.requestText();
            String normalized = RepeaterRequestNormalizer.normalizeGetToPost(original).orElse(null);
            if (normalized != null && !normalized.equals(original)) {
                JTextComponent editor = target.editor();
                uiUpdates.add(() -> editor.setText(normalized));
                uiUpdates.add(() -> panel.appendOutput("[system] Updated " + target.tabPath()));
                modified++;
            }
        }
        int scanned = targets.size();
        int changed = modified;
        if (!uiUpdates.isEmpty()) {
            Edt.runLater(() -> uiUpdates.forEach(Runnable::run));
        }
        Edt.runLater(() -> panel.setBusy(false, "Updated " + changed + " of " + scanned + " tabs"));
        return new RepeaterBatchResult(scanned, changed);
    }

    private void wireActions() {
        panel.scan.addActionListener(event -> scan());
        panel.normalizeSelected.addActionListener(event -> normalizeSelected());
        panel.normalizeAll.addActionListener(event -> normalizeAll());
        panel.clear.addActionListener(event -> panel.clearResults());
    }
}
