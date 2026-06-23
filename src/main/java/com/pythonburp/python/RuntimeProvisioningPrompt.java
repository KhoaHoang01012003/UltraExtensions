package com.pythonburp.python;

import com.pythonburp.concurrency.Edt;

import javax.swing.JOptionPane;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

@FunctionalInterface
interface RuntimeProvisioningPrompt {
    boolean requestProvisioning(Path runtimeRoot, IOException failure);

    static RuntimeProvisioningPrompt swing() {
        return (runtimeRoot, failure) -> {
            AtomicInteger answer = new AtomicInteger(JOptionPane.NO_OPTION);
            Edt.runAndWait(() -> answer.set(
                JOptionPane.showConfirmDialog(
                    null,
                    message(runtimeRoot, failure),
                    "Administrator Permission Required",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE)));
            return answer.get() == JOptionPane.YES_OPTION;
        };
    }

    private static String message(Path runtimeRoot, IOException failure) {
        return "Python IDE needs administrator permission to prepare its embedded runtime under:\n"
            + runtimeRoot
            + "\n\nWindows blocked write access with:\n"
            + failure.getMessage()
            + "\n\nClick Yes to allow a one-time administrator setup now.";
    }
}
