package com.pythonburp.repeater;

import javax.swing.text.JTextComponent;

public record RepeaterTabSnapshot(String windowTitle, String tabPath, JTextComponent editor, String requestText) {
}
