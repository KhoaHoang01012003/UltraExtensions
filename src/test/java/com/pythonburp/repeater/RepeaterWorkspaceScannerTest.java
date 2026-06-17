package com.pythonburp.repeater;

import org.junit.jupiter.api.Test;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RepeaterWorkspaceScannerTest {
    @Test
    void findsRequestEditorsInsideRepeaterTabs() throws Exception {
        JFrame frame = new JFrame("Burp Suite");
        try {
            SwingUtilities.invokeAndWait(() -> {
                JTabbedPane suiteTabs = new JTabbedPane();
                JTabbedPane repeaterTabs = new JTabbedPane();
                JPanel requestTab = new JPanel(new BorderLayout());
                requestTab.add(new JTextArea("""
                    GET /submit?a=1&b=2 HTTP/1.1
                    Host: example.com

                    """));
                repeaterTabs.addTab("Req 1", requestTab);
                suiteTabs.addTab("Repeater", repeaterTabs);
                frame.setContentPane(suiteTabs);
                frame.pack();
                frame.setVisible(true);
            });

            List<RepeaterTabSnapshot> snapshots = new RepeaterWorkspaceScanner().scanOpenTabs();

            assertEquals(1, snapshots.size());
            assertTrue(snapshots.get(0).requestText().contains("GET /submit"));
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
