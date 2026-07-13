package com.pythonburp.python;

import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.storage.ExtensionDataPaths;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class CPythonRuntimeFactory implements Supplier<PythonRuntime> {
    public static final String HELPER_RESOURCE_ROOT = "/cpython/windows-x64/Lib/site-packages/burp";
    public static final String HELPER_STAGE_ID = "python-worker-burp-rpc2";
    public static final String PIP_RESOURCE_ROOT = "/cpython/windows-x64/Lib/site-packages/pip";
    public static final String PIP_STAGE_ID = "python-worker-pip-bootstrap1";
    public static final String COMPAT_RESOURCE_ROOT = "/cpython/windows-x64/python-compat-3.14";
    public static final String COMPAT_STAGE_ID = "python-worker-compat3142";

    private final NmapRuntimePaths runtimePaths;
    private final ExtensionDataPaths paths;
    private final PythonRuntimeEnvironment environment;
    private final boolean pipAvailable;
    private final Path pipBootstrapRoot;
    private final Path stdlibFallbackRoot;
    private final Path compatNativeRoot;
    private final String pipProbeWarning;
    private final Supplier<Path> helperRootSupplier;

    public CPythonRuntimeFactory() {
        this(NmapRuntimePaths.fixed(), ExtensionDataPaths.windowsDefault());
    }

    public CPythonRuntimeFactory(ExtensionDataPaths paths) {
        this(NmapRuntimePaths.fixed(), paths);
    }

    public CPythonRuntimeFactory(Path zenmapBin, ExtensionDataPaths paths) {
        this(new NmapRuntimePaths(Objects.requireNonNull(zenmapBin, "zenmapBin")), paths);
    }

    CPythonRuntimeFactory(NmapRuntimePaths runtimePaths, ExtensionDataPaths paths) {
        this(probeRuntime(
            Objects.requireNonNull(runtimePaths, "runtimePaths"),
            Objects.requireNonNull(paths, "paths")
        ));
    }

    public CPythonRuntimeFactory(PythonRuntimeEnvironment environment, ExtensionDataPaths paths) {
        this(NmapRuntimePaths.fixed(), paths, environment, environment.pipAvailable(), null, null, null, null, null);
    }

    public CPythonRuntimeFactory(PythonRuntimeEnvironment environment, ExtensionDataPaths paths, Supplier<Path> helperRootSupplier) {
        this(NmapRuntimePaths.fixed(), paths, environment, environment.pipAvailable(), null, null, null, null, helperRootSupplier);
    }

    private CPythonRuntimeFactory(NmapRuntimePaths runtimePaths, ExtensionDataPaths paths,
                                  PythonRuntimeEnvironment environment, boolean pipAvailable, Path pipBootstrapRoot,
                                  Path stdlibFallbackRoot, Path compatNativeRoot,
                                  String pipProbeWarning, Supplier<Path> helperRootSupplier) {
        this.runtimePaths = Objects.requireNonNull(runtimePaths, "runtimePaths");
        this.paths = Objects.requireNonNull(paths, "paths");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.pipAvailable = pipAvailable;
        this.pipBootstrapRoot = pipBootstrapRoot == null ? null : pipBootstrapRoot.toAbsolutePath().normalize();
        this.stdlibFallbackRoot = stdlibFallbackRoot == null ? null : stdlibFallbackRoot.toAbsolutePath().normalize();
        this.compatNativeRoot = compatNativeRoot == null ? null : compatNativeRoot.toAbsolutePath().normalize();
        this.pipProbeWarning = pipProbeWarning;
        validateEnvironment(environment);
        this.helperRootSupplier = helperRootSupplier == null ? () -> {
            try {
                return prepareHelperRoot();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to stage bundled Python helper assets", e);
            }
        } : helperRootSupplier;
    }

    @Override
    public PythonRuntime get() {
        return get(new BurpBridge(), InteractiveInputHandler.disabled());
    }

    public PythonRuntime get(BurpBridge bridge) {
        return get(bridge, InteractiveInputHandler.disabled());
    }

    public PythonRuntime get(BurpBridge bridge, InteractiveInputHandler inputHandler) {
        return new CPythonWorkerRuntime(
            CPythonWorkerCommand.forExecutable(environment.executable()),
            paths.runtimeWorkRoot(environment.environmentKey()),
            bridge,
            userPackages(),
            helperRootSupplier.get(),
            extraPythonPaths(),
            stdlibFallbackRoot,
            compatNativeRoot,
            pipBootstrapRoot,
            inputHandler
        );
    }

    public Path userPackages() {
        return paths.userPackages(environment.environmentKey());
    }

    public PythonRuntimeEnvironment environment() {
        return environment;
    }

    public boolean pipAvailable() {
        return pipAvailable;
    }

    public boolean usingBundledPipFallback() {
        return pipAvailable && !environment.pipAvailable() && pipBootstrapRoot != null;
    }

    public Map<String, String> pipEnvironmentOverrides() {
        LinkedHashMap<String, String> overrides = new LinkedHashMap<>();
        String pythonPath = joinedPaths(stdlibFallbackRoot, compatNativeRoot, pipBootstrapRoot);
        if (!pythonPath.isBlank()) {
            overrides.put("PYTHONPATH", pythonPath);
        }
        if (stdlibFallbackRoot != null) {
            overrides.put(PythonRuntimeBootstrap.ENV_FALLBACK_STDLIB_ROOT, stdlibFallbackRoot.toString());
        }
        if (compatNativeRoot != null) {
            overrides.put(PythonRuntimeBootstrap.ENV_COMPAT_NATIVE_ROOT, compatNativeRoot.toString());
            overrides.put("PATH", compatNativeRoot.toString());
        }
        if (pipBootstrapRoot != null) {
            overrides.put(PythonRuntimeBootstrap.ENV_PIP_ROOT, pipBootstrapRoot.toString());
        }
        return Map.copyOf(overrides);
    }

    public String pipProbeWarning() {
        return pipProbeWarning;
    }

    public Path stdlibFallbackRoot() {
        return stdlibFallbackRoot;
    }

    public Path compatNativeRoot() {
        return compatNativeRoot;
    }

    private Path prepareHelperRoot() throws IOException {
        return new ResourceDirectoryStager(
            paths.runtimeAssetsRoot(environment.environmentKey()),
            CPythonRuntimeFactory.class,
            HELPER_RESOURCE_ROOT,
            "burp",
            HELPER_STAGE_ID
        ).stage();
    }

    private List<Path> extraPythonPaths() {
        ArrayList<Path> paths = new ArrayList<>();
        if (compatNativeRoot != null) {
            paths.add(compatNativeRoot);
        }
        if (stdlibFallbackRoot != null) {
            paths.add(stdlibFallbackRoot);
        }
        if (pipBootstrapRoot != null) {
            paths.add(pipBootstrapRoot);
        }
        return List.copyOf(paths);
    }

    public Path pythonExecutable() {
        return environment.executable();
    }

    private static ProbedRuntime probeRuntime(NmapRuntimePaths runtimePaths, ExtensionDataPaths paths) {
        try {
            Path executable = runtimePaths.pythonExecutable();
            ProbeResult metadata = run(executable, "-c",
                "import os, platform, sys; stdlib = os.path.dirname(getattr(os, '__file__', '') or ''); "
                    + "stdlib = os.path.normpath(os.path.join(os.path.dirname(sys.executable), stdlib)) if stdlib and not os.path.isabs(stdlib) else (os.path.abspath(stdlib) if stdlib else ''); "
                    + "print('|'.join([str(sys.version_info[0]), str(sys.version_info[1]), str(sys.version_info[2]), platform.system(), platform.machine(), sys.executable, stdlib]))");
            if (!metadata.succeeded()) {
                throw new IOException("Python metadata probe failed: " + metadata.describeFailure());
            }

            String[] parts = metadata.stdout().strip().split("\\|", 7);
            if (parts.length < 7) {
                throw new IOException("Unexpected Python metadata output: " + metadata.stdout().strip());
            }

            Path stdlibRoot = parts[6] == null || parts[6].isBlank() ? null : Path.of(parts[6]).toAbsolutePath().normalize();

            PythonRuntimeEnvironment environment = new PythonRuntimeEnvironment(
                Path.of(parts[5]),
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                parts[3],
                parts[4],
                false
            );

            CompatibilityRoots compatibilityRoots = stageBundledCompatibilityRoots(paths, environment.environmentKey());
            Path stdlibFallbackRoot = compatibilityRoots.stdlibRoot() == null ? stdlibRoot : compatibilityRoots.stdlibRoot();
            Path compatNativeRoot = compatibilityRoots.nativeRoot();

            ProbeResult nativePip = run(executable, probeEnvironment(stdlibFallbackRoot, compatNativeRoot, null), pipProbeArguments("--version"));
            if (nativePip.succeeded()) {
                return new ProbedRuntime(
                    runtimePaths,
                    paths,
                    new PythonRuntimeEnvironment(
                        environment.executable(),
                        environment.major(),
                        environment.minor(),
                        environment.micro(),
                        environment.platform(),
                        environment.architecture(),
                        true
                    ),
                    true,
                    null,
                    stdlibFallbackRoot,
                    compatNativeRoot,
                    null
                );
            }

            Path pipBootstrapRoot = stageBundledPipRoot(paths, environment.environmentKey(), stdlibFallbackRoot);
            ProbeResult bundledPip = run(
                executable,
                probeEnvironment(stdlibFallbackRoot, compatNativeRoot, pipBootstrapRoot),
                pipProbeArguments("--version")
            );
            String pipProbeWarning = bundledPip.succeeded()
                ? null
                : "Bundled pip startup probe failed: " + bundledPip.describeFailure();
            return new ProbedRuntime(
                runtimePaths,
                paths,
                environment,
                true,
                pipBootstrapRoot,
                stdlibFallbackRoot,
                compatNativeRoot,
                pipProbeWarning
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to probe Zenmap Python at " + runtimePaths.zenmapBin() + ": " + e.getMessage(), e);
        }
    }

    private static CompatibilityRoots stageBundledCompatibilityRoots(ExtensionDataPaths paths, String environmentKey) throws IOException {
        Path stageRoot = new ResourceDirectoryStager(
            paths.runtimeAssetsRoot(environmentKey + "-compat-python"),
            CPythonRuntimeFactory.class,
            COMPAT_RESOURCE_ROOT,
            "compat",
            COMPAT_STAGE_ID
        ).stage();
        Path compatRoot = stageRoot.resolve("compat").normalize();
        Path stdlibRoot = compatRoot.resolve("Lib").normalize();
        Path nativeRoot = compatRoot.resolve("DLLs").normalize();
        return new CompatibilityRoots(
            Files.isDirectory(stdlibRoot) ? stdlibRoot : null,
            Files.isDirectory(nativeRoot) ? nativeRoot : null
        );
    }

    private static Path stageBundledPipRoot(ExtensionDataPaths paths, String environmentKey, Path stdlibFallbackRoot) throws IOException {
        Path root = new ResourceDirectoryStager(
            paths.runtimeAssetsRoot(environmentKey + "-bundled-pip"),
            CPythonRuntimeFactory.class,
            PIP_RESOURCE_ROOT,
            "pip",
            PIP_STAGE_ID
        ).stage();
        if (stdlibFallbackRoot != null && Files.exists(stdlibFallbackRoot)) {
            Files.writeString(root.resolve(".stdlib-root-path"), stdlibFallbackRoot.toString(), StandardCharsets.UTF_8);
        }
        return root;
    }

    private static void validateEnvironment(PythonRuntimeEnvironment environment) {
        if (!environment.isPython3()) {
            throw new IllegalStateException(
                "Zenmap Python must be Python 3, but detected " + environment.version() + "."
            );
        }
    }

    private static ProbeResult run(Path executable, String... arguments) throws IOException {
        return run(executable, Map.of(), arguments);
    }

    private static ProbeResult run(Path executable, Map<String, String> environmentOverrides, String... arguments) throws IOException {
        return run(executable, environmentOverrides, List.of(arguments));
    }

    private static ProbeResult run(Path executable, Map<String, String> environmentOverrides, List<String> arguments) throws IOException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command().add(executable.toString());
        builder.command().addAll(arguments);
        applyEnvironment(builder.environment(), environmentOverrides);
        Process process = builder.start();

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread out = reader(process.getInputStream(), stdout, "burp-python-probe-stdout");
        Thread err = reader(process.getErrorStream(), stderr, "burp-python-probe-stderr");
        try {
            if (!process.waitFor(Duration.ofSeconds(20).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("Timed out while probing Zenmap Python.");
            }
            out.join(java.util.concurrent.TimeUnit.SECONDS.toMillis(2));
            err.join(java.util.concurrent.TimeUnit.SECONDS.toMillis(2));
            return new ProbeResult(process.exitValue(), text(stdout), text(stderr));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while probing Zenmap Python.", e);
        }
    }

    private static void applyEnvironment(Map<String, String> target, Map<String, String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank() || value == null || value.isBlank()) {
                continue;
            }
            if ("PYTHONPATH".equalsIgnoreCase(key)) {
                String existing = target.getOrDefault(key, "");
                target.put(key, existing == null || existing.isBlank()
                    ? value
                    : value + java.io.File.pathSeparator + existing);
                continue;
            }
            if ("PATH".equalsIgnoreCase(key)) {
                String existing = target.getOrDefault(key, "");
                target.put(key, existing == null || existing.isBlank()
                    ? value
                    : value + java.io.File.pathSeparator + existing);
                continue;
            }
            target.put(key, value);
        }
    }

    private static Map<String, String> probeEnvironment(Path stdlibFallbackRoot, Path compatNativeRoot, Path pipBootstrapRoot) {
        LinkedHashMap<String, String> overrides = new LinkedHashMap<>();
        String pythonPath = joinedPaths(stdlibFallbackRoot, compatNativeRoot, pipBootstrapRoot);
        if (!pythonPath.isBlank()) {
            overrides.put("PYTHONPATH", pythonPath);
        }
        if (stdlibFallbackRoot != null) {
            overrides.put(PythonRuntimeBootstrap.ENV_FALLBACK_STDLIB_ROOT, stdlibFallbackRoot.toString());
        }
        if (compatNativeRoot != null) {
            overrides.put(PythonRuntimeBootstrap.ENV_COMPAT_NATIVE_ROOT, compatNativeRoot.toString());
            overrides.put("PATH", compatNativeRoot.toString());
        }
        if (pipBootstrapRoot != null) {
            overrides.put(PythonRuntimeBootstrap.ENV_PIP_ROOT, pipBootstrapRoot.toString());
        }
        return Map.copyOf(overrides);
    }

    private static List<String> pipProbeArguments(String... pipArguments) {
        ArrayList<String> arguments = new ArrayList<>();
        arguments.add("-c");
        arguments.add(PythonRuntimeBootstrap.pipBootstrapCommand());
        arguments.addAll(List.of(pipArguments));
        return List.copyOf(arguments);
    }

    private static String joinedPaths(Path... paths) {
        return java.util.Arrays.stream(paths)
            .filter(Objects::nonNull)
            .map(Path::toString)
            .reduce((left, right) -> left + File.pathSeparator + right)
            .orElse("");
    }

    private static Thread reader(java.io.InputStream input, ByteArrayOutputStream output, String name) {
        Thread thread = new Thread(() -> {
            try (input) {
                input.transferTo(output);
            } catch (IOException ignored) {
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static String text(ByteArrayOutputStream stream) {
        return stream.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private record ProbeResult(int exitCode, String stdout, String stderr) {
        boolean succeeded() {
            return exitCode == 0;
        }

        String describeFailure() {
            String details = stderr == null || stderr.isBlank() ? stdout : stderr;
            return "exit code " + exitCode + (details == null || details.isBlank() ? "" : ": " + details.strip());
        }
    }

    private record ProbedRuntime(NmapRuntimePaths runtimePaths, ExtensionDataPaths paths,
                                 PythonRuntimeEnvironment environment, boolean pipAvailable,
                                 Path pipBootstrapRoot, Path stdlibFallbackRoot, Path compatNativeRoot,
                                 String pipProbeWarning) {
        private ProbedRuntime {
            Objects.requireNonNull(runtimePaths, "runtimePaths");
            Objects.requireNonNull(paths, "paths");
            Objects.requireNonNull(environment, "environment");
        }
    }

    private record CompatibilityRoots(Path stdlibRoot, Path nativeRoot) {
    }

    private CPythonRuntimeFactory(ProbedRuntime probedRuntime) {
        this(
            probedRuntime.runtimePaths(),
            probedRuntime.paths(),
            probedRuntime.environment(),
            probedRuntime.pipAvailable(),
            probedRuntime.pipBootstrapRoot(),
            probedRuntime.stdlibFallbackRoot(),
            probedRuntime.compatNativeRoot(),
            probedRuntime.pipProbeWarning(),
            null
        );
    }
}
