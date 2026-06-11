package com.pythonburp.ui;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class EditorPanel extends JPanel {
    private final JTextArea editor = new JTextArea();

    public EditorPanel() {
        super(new BorderLayout());
        editor.setEditable(true);
        editor.setEnabled(true);
        editor.setFocusable(true);
        editor.setRequestFocusEnabled(true);
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        editor.setTabSize(4);
        editor.setLineWrap(false);
        editor.setText("print('Hello from Burp Python IDE')\n");
        JScrollPane scrollPane = new JScrollPane(editor);
        scrollPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                focusEditor();
            }
        });
        add(scrollPane, BorderLayout.CENTER);
        SwingUtilities.invokeLater(this::focusEditor);
    }

    public String source() {
        return editor.getText();
    }

    public void setSource(String source) {
        editor.setText(source);
        editor.setCaretPosition(0);
        focusEditor();
    }

    public boolean isEditorReadyForInput() {
        return editor.isEditable() && editor.isEnabled() && editor.isFocusable() && editor.isRequestFocusEnabled();
    }

    JComponent editorComponent() {
        return editor;
    }

    public void focusEditor() {
        editor.requestFocusInWindow();
    }
}
