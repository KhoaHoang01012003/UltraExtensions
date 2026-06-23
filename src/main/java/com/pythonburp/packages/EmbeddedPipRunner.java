package com.pythonburp.packages;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class EmbeddedPipRunner {
    private volatile Process activeProcess;

    public PipRunResult run(List<String> command, Consumer<String> output) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).start();
        activeProcess = process;
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread out = reader(process.getInputStream(), stdout, output, false);
        Thread err = reader(process.getErrorStream(), stderr, output, true);
        try {
            int exitCode = process.waitFor();
            out.join(TimeUnit.SECONDS.toMillis(5));
            err.join(TimeUnit.SECONDS.toMillis(5));
            return new PipRunResult(exitCode, false, stdout.toString(), stderr.toString());
        } finally {
            activeProcess = null;
        }
    }

    public void cancel() {
        Process process = activeProcess;
        if (process != null && process.isAlive()) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    private static Thread reader(InputStream input, StringBuilder sink, Consumer<String> output, boolean error) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (sink) { sink.append(line).append(System.lineSeparator()); }
                    output.accept((error ? "[err] " : "") + line);
                }
            } catch (IOException ignored) {
            }
        }, error ? "burp-python-pip-stderr" : "burp-python-pip-stdout");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
