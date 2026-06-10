package com.pythonburp.concurrency;

import javax.swing.SwingUtilities;

public final class Edt {
    private Edt() {
    }

    public static boolean isEdt() {
        return SwingUtilities.isEventDispatchThread();
    }

    public static void runLater(Runnable runnable) {
        SwingUtilities.invokeLater(runnable);
    }

    public static void requireEdt() {
        if (!isEdt()) {
            throw new IllegalStateException("Swing UI mutation must run on the Event Dispatch Thread");
        }
    }
}
