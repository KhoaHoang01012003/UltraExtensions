package com.pythonburp.ui;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Component;
import java.awt.Dimension;

public final class HelpDialogs {
    private HelpDialogs() {
    }

    public static void showPythonIdeHelp(Component parent) {
        JTextArea text = new JTextArea("""
            Editor and Run Modes
            - Editor Script: runs the Python code currently open in the editor.
            - Custom Command: runs the raw command tail after python.exe, for example: -m abc -h xyz
            - Interactive: lets scripts answer input() prompts through the console input box.

            Editor Toolbar
            - Load: open a .py file into the editor.
            - Save As: save the current editor content to a .py file.
            - Run: start the selected mode.
            - Stop: stop the current script or custom command.
            - Clear Log: clear the output console.
            - Help: show this quick reference.

            Custom Command Notes
            - Enter only the part after python.exe.
            - Examples:
              -m http.server 8000
              -m abc -h xyz
              C:\\path\\tool.py --target example.com
            - Shell syntax such as pipes, redirects, and cmd.exe operators is not supported.

            Package Manager Buttons
            - Install: install a PyPI requirement from the text box.
            - Install Wheel: install a local .whl file into the extension package area.
            - Install Requirements: install packages from a requirements .txt file.
            - Refresh: reload the current package inventory.
            - Settings: configure index URLs, proxy, trusted hosts, and timeout.
            - Uninstall Selected: remove the selected package from managed requests and rebuild the package environment.
            - Clear User Packages: delete the managed user package directory in LOCALAPPDATA and recreate it empty.
            - Clear pip Cache: delete the extension pip cache.
            - Reset All Extension Data: remove all extension-managed data under LOCALAPPDATA and require reload.
            - Help: show this quick reference.
            """);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(text);
        scrollPane.setPreferredSize(new Dimension(640, 420));
        JOptionPane.showMessageDialog(parent, scrollPane, "Burp Python IDE Help", JOptionPane.INFORMATION_MESSAGE);
    }
}
