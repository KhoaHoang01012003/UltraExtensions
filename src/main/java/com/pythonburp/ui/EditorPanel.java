package com.pythonburp.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.JPanel;
import java.awt.BorderLayout;

public final class EditorPanel extends JPanel {
    private final RSyntaxTextArea editor = new RSyntaxTextArea();

    public EditorPanel() {
        super(new BorderLayout());
        editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
        editor.setCodeFoldingEnabled(true);
        editor.setText("print('Hello from Burp Python IDE')\n");
        add(new RTextScrollPane(editor), BorderLayout.CENTER);
    }

    public String source() {
        return editor.getText();
    }
}
