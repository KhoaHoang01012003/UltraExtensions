package com.pythonburp.concurrency;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;

public final class Edt {
    private Edt() {
    }

    public static boolean isEdt() {
        return SwingUtilities.isEventDispatchThread();
    }

    public static void runLater(Runnable runnable) {
        SwingUtilities.invokeLater(runnable);
    }

    public static void runAndWait(Runnable runnable) {
        if (isEdt()) {
            runnable.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(runnable);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Swing UI initialization", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Swing UI initialization failed", cause);
        }
    }

    public static void requireEdt() {
        if (!isEdt()) {
            throw new IllegalStateException("Swing UI mutation must run on the Event Dispatch Thread");
        }
    }
}
