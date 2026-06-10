package com.pythonburp.ui;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

public final class StatusBar extends JPanel {
    private final JLabel label = new JLabel("Ready");

    public StatusBar() {
        super(new BorderLayout());
        add(label, BorderLayout.CENTER);
    }

    public void setStatus(String status) {
        label.setText(status);
    }
}
