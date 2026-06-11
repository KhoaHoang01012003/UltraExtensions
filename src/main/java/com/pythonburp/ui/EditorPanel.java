package com.pythonburp.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class EditorPanel extends JPanel {
    private final RSyntaxTextArea editor = new RSyntaxTextArea();

    public EditorPanel() {
        super(new BorderLayout());
        editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
        editor.setCodeFoldingEnabled(true);
        editor.setEditable(true);
        editor.setEnabled(true);
        editor.setFocusable(true);
        editor.setRequestFocusEnabled(true);
        editor.setText("print('Hello from Burp Python IDE')\n");
        RTextScrollPane scrollPane = new RTextScrollPane(editor);
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

    public void focusEditor() {
        editor.requestFocusInWindow();
    }
}
