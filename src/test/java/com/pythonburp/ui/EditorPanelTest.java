package com.pythonburp.ui;

import org.junit.jupiter.api.Test;

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

    private static void onEdt(Runnable runnable) throws InvocationTargetException, InterruptedException {
        SwingUtilities.invokeAndWait(runnable);
    }
}
