package com.pythonburp.python;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class CPythonWorkerRuntime implements PythonRuntime {
    private final CPythonWorkerCommand command;
    private final Path workingDirectory;

    public CPythonWorkerRuntime(CPythonWorkerCommand command, Path workingDirectory) {
        this.command = Objects.requireNonNull(command, "command");
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory").toAbsolutePath().normalize();
    }

    @Override
    public ScriptRunResult execute(String source, Duration timeout) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(timeout, "timeout");

        Path script = null;
        try {
            Files.createDirectories(workingDirectory);
            script = Files.createTempFile(workingDirectory, "burp-python-", ".py");
            Files.writeString(script, source);

            Process process = new ProcessBuilder(command.commandFor(script))
                .directory(workingDirectory.toFile())
                .start();

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            Thread stdoutReader = reader(process.getInputStream(), stdout, "burp-python-cpython-stdout");
            Thread stderrReader = reader(process.getErrorStream(), stderr, "burp-python-cpython-stderr");
            boolean finished = waitFor(process, timeout);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                join(stdoutReader);
                join(stderrReader);
                return ScriptRunResult.failed(text(stdout), text(stderr), "Script timed out after " + timeout);
            }

            join(stdoutReader);
            join(stderrReader);
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return ScriptRunResult.succeeded(text(stdout), text(stderr));
            }
            return ScriptRunResult.failed(text(stdout), text(stderr), "CPython worker exited with exit code " + exitCode);
        } catch (IOException e) {
            return ScriptRunResult.failed("", "", e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ScriptRunResult.failed("", "", "Interrupted while waiting for CPython worker");
        } finally {
            if (script != null) {
                try {
                    Files.deleteIfExists(script);
                } catch (IOException ignored) {
                    // Best effort: script files live under the extension worker cache.
                }
            }
        }
    }

    @Override
    public void close() {
    }

    private static boolean waitFor(Process process, Duration timeout) throws InterruptedException {
        if (timeout.isZero() || timeout.isNegative()) {
            return process.waitFor() >= 0;
        }
        return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static Thread reader(java.io.InputStream input, ByteArrayOutputStream output, String name) {
        Thread thread = new Thread(() -> {
            try (input) {
                input.transferTo(output);
            } catch (IOException ignored) {
                // The process may be killed during timeout handling.
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void join(Thread thread) throws InterruptedException {
        thread.join(TimeUnit.SECONDS.toMillis(5));
    }

    private static String text(ByteArrayOutputStream stream) {
        return stream.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
