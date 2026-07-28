package com.pythonburp.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EditorPanelTest {
    @Test
    void sourceCanBeEditedProgrammatically() throws Exception {
        AtomicReference<String> source = new AtomicReference<>();
        onEdt(() -> {
            EditorPanel panel = new EditorPanel();
            panel.setSource("print('changed')\n");
            source.set(panel.source());
        });

        assertEquals("print('changed')\n", source.get());
    }

    @Test
    void editorIsEditableAndEnabled() throws Exception {
        AtomicBoolean ready = new AtomicBoolean(false);
        onEdt(() -> {
            EditorPanel panel = new EditorPanel();
            ready.set(panel.isEditorReadyForInput());
        });

        assertTrue(ready.get());
    }

    @Test
    void usesStandardSwingTextAreaForBurpHostCompatibility() throws Exception {
        AtomicBoolean standardTextArea = new AtomicBoolean(false);
        onEdt(() -> {
            EditorPanel panel = new EditorPanel();
            standardTextArea.set(panel.editorComponent() instanceof JTextArea);
        });

        assertTrue(standardTextArea.get());
    }

    private static void onEdt(Runnable runnable) throws InvocationTargetException, InterruptedException {
        SwingUtilities.invokeAndWait(runnable);
    }
}
