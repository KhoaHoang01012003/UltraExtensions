package com.pythonburp.python;

import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.bridge.HttpBridge;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

public final class CPythonWorkerRuntime implements PythonRuntime {
    private final CPythonWorkerCommand command;
    private final Path workingDirectory;
    private final BurpBridge bridge;
    private final Path userPackages;

    public CPythonWorkerRuntime(CPythonWorkerCommand command, Path workingDirectory) {
        this(command, workingDirectory, new BurpBridge(), workingDirectory.resolve("user-packages"));
    }

    public CPythonWorkerRuntime(CPythonWorkerCommand command, Path workingDirectory, BurpBridge bridge) {
        this(command, workingDirectory, bridge, workingDirectory.resolve("user-packages"));
    }

    public CPythonWorkerRuntime(CPythonWorkerCommand command, Path workingDirectory, BurpBridge bridge, Path userPackages) {
        this.command = Objects.requireNonNull(command, "command");
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory").toAbsolutePath().normalize();
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.userPackages = Objects.requireNonNull(userPackages, "userPackages").toAbsolutePath().normalize();
    }

    @Override
    public ScriptRunResult execute(String source, Duration timeout) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(timeout, "timeout");

        Path script = null;
        Path launcher = null;
        Path rpcDirectory = null;
        try {
            Files.createDirectories(workingDirectory);
            Files.createDirectories(userPackages);
            script = Files.createTempFile(workingDirectory, "burp-python-", ".py");
            Files.writeString(script, source);
            launcher = Files.createTempFile(workingDirectory, "burp-python-launcher-", ".py");
            Files.writeString(launcher, """
                import os
                import runpy
                import sys
                user_packages = os.environ.get("BURP_PYTHON_USER_PACKAGES", "")
                if user_packages:
                    sys.path.insert(0, user_packages)
                runpy.run_path(sys.argv[1], run_name="__main__")
                """, StandardCharsets.UTF_8);
            rpcDirectory = Files.createTempDirectory(workingDirectory, "rpc-");

            ProcessBuilder builder = new ProcessBuilder(command.commandFor(launcher, script))
                .directory(workingDirectory.toFile());
            builder.environment().put("BURP_PYTHON_RPC_DIR", rpcDirectory.toString());
            builder.environment().put("BURP_PYTHON_USER_PACKAGES", userPackages.toString());
            String existingPythonPath = builder.environment().getOrDefault("PYTHONPATH", "");
            builder.environment().put("PYTHONPATH", existingPythonPath.isBlank()
                ? userPackages.toString()
                : userPackages + File.pathSeparator + existingPythonPath);
            Process process = builder.start();

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            Thread stdoutReader = reader(process.getInputStream(), stdout, "burp-python-cpython-stdout");
            Thread stderrReader = reader(process.getErrorStream(), stderr, "burp-python-cpython-stderr");
            boolean finished = waitFor(process, timeout, rpcDirectory);
            if (!finished) {
                terminate(process);
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
            if (launcher != null) {
                try {
                    Files.deleteIfExists(launcher);
                } catch (IOException ignored) {
                }
            }
            if (rpcDirectory != null) {
                try {
                    deleteTree(rpcDirectory);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Override
    public void close() {
    }

    private boolean waitFor(Process process, Duration timeout, Path rpcDirectory) throws InterruptedException, IOException {
        if (timeout.isZero() || timeout.isNegative()) {
            while (process.isAlive()) {
                dispatchRpcRequests(rpcDirectory);
                Thread.sleep(25);
            }
            dispatchRpcRequests(rpcDirectory);
            return true;
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout.toMillis());
        while (process.isAlive() && System.nanoTime() < deadline) {
            dispatchRpcRequests(rpcDirectory);
            Thread.sleep(25);
        }
        dispatchRpcRequests(rpcDirectory);
        return !process.isAlive();
    }

    private void dispatchRpcRequests(Path rpcDirectory) throws IOException {
        try (var paths = Files.list(rpcDirectory)) {
            for (Path request : paths
                .filter(path -> path.getFileName().toString().endsWith(".request"))
                .toList()) {
                Path response = responsePath(request);
                if (Files.exists(response)) {
                    Files.deleteIfExists(request);
                    continue;
                }
                Map<String, String> fields = readFields(request);
                if (!"1".equals(fields.get("__end"))) {
                    continue;
                }
                writeFields(response, dispatch(fields));
                Files.deleteIfExists(request);
            }
        }
    }

    private Map<String, String> dispatch(Map<String, String> fields) {
        String operation = fields.getOrDefault("operation", "");
        if ("http.send".equals(operation)) {
            HttpBridge.HttpResult result = bridge.http().send(
                fields.getOrDefault("method", "GET"),
                fields.getOrDefault("url", ""),
                fields.getOrDefault("body", "")
            );
            Map<String, String> response = new LinkedHashMap<>();
            response.put("ok", "true");
            response.put("statusCode", Integer.toString(result.statusCode()));
            response.put("body", result.body());
            return response;
        }
        Map<String, String> response = new LinkedHashMap<>();
        response.put("ok", "false");
        response.put("error", "Unsupported RPC operation: " + operation);
        return response;
    }

    private static Path responsePath(Path request) {
        String name = request.getFileName().toString();
        return request.resolveSibling(name.substring(0, name.length() - ".request".length()) + ".response");
    }

    private static Map<String, String> readFields(Path request) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : Files.readAllLines(request, StandardCharsets.UTF_8)) {
            if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                line = line.substring(1);
            }
            int equals = line.indexOf('=');
            if (equals > 0) {
                fields.put(line.substring(0, equals), line.substring(equals + 1));
            }
        }
        return fields;
    }

    private static void writeFields(Path response, Map<String, String> fields) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            builder.append(entry.getKey())
                .append('=')
                .append(entry.getValue() == null ? "" : entry.getValue().replace("\r", "\\r").replace("\n", "\\n"))
                .append(System.lineSeparator());
        }
        Files.writeString(response, builder.toString(), StandardCharsets.UTF_8);
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

    private static void terminate(Process process) throws InterruptedException {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        process.waitFor(10, TimeUnit.SECONDS);
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.descendants().forEach(descendant -> {
            try {
                descendant.onExit().get(10, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // Best effort: timeout handling must return a failed script result.
            }
        });
    }

    private static void join(Thread thread) throws InterruptedException {
        thread.join(TimeUnit.SECONDS.toMillis(5));
    }

    private static String text(ByteArrayOutputStream stream) {
        return stream.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var entries = Files.walk(root)) {
            for (Path path : entries.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
