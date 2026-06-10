package com.pythonburp.python;

import com.pythonburp.bridge.BurpBridge;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class GraalPyPythonRuntime implements PythonRuntime {
    private static final String RESOURCE_DIRECTORY = "GRAALPY-VFS/com.pythonburp/burp-python-ide";
    private static final String BURP_RESOURCE_ROOT = "/" + RESOURCE_DIRECTORY + "/src/burp";
    private static final Path WORKING_DIRECTORY = Path.of("").toAbsolutePath().normalize();
    private final BurpBridge bridge;

    public GraalPyPythonRuntime(BurpBridge bridge) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
    }

    @Override
    public ScriptRunResult execute(String source) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Path extractionRoot = null;
        try (VirtualFileSystem fileSystem = VirtualFileSystem.newBuilder()
            .resourceDirectory(RESOURCE_DIRECTORY)
            .resourceLoadingClass(GraalPyPythonRuntime.class)
            .build();
             Context context = GraalPyResources.contextBuilder(fileSystem)
                 .allowAllAccess(true)
                 .allowIO(IOAccess.ALL)
                 .currentWorkingDirectory(WORKING_DIRECTORY)
                 .out(stdout)
                 .err(stderr)
                 .build()) {
            extractionRoot = Files.createTempDirectory("burp-python-vfs-");
            GraalPyResources.extractVirtualFileSystemResources(fileSystem, extractionRoot);

            context.getBindings("python").putMember("burpBridge", bridge);
            context.getBindings("python").putMember("burpModuleRoot", fileSystem.getMountPoint() + "/src");
            context.getBindings("python").putMember("burpResourceModuleRoot", "/" + RESOURCE_DIRECTORY + "/src");
            context.getBindings("python").putMember("venvSitePackages", fileSystem.getMountPoint() + "/venv/Lib/site-packages");
            context.getBindings("python").putMember("resourceVenvSitePackages", "/" + RESOURCE_DIRECTORY + "/venv/Lib/site-packages");
            List<String> extractedPaths = extractedModulePaths(extractionRoot);
            context.getBindings("python").putMember("extractedModulePaths", extractedPaths.toArray(String[]::new));
            context.getBindings("python").putMember("burpInitSource", loadResource("burp/__init__.py"));
            context.getBindings("python").putMember("burpEncoderSource", loadResource("burp/encoder.py"));
            context.getBindings("python").putMember("burpCryptoSource", loadResource("burp/crypto.py"));
            context.eval("python", """
                import builtins
                import sys
                import types

                builtins.burpBridge = burpBridge
                for candidate in (burpModuleRoot, burpResourceModuleRoot, venvSitePackages, resourceVenvSitePackages):
                    if candidate not in sys.path:
                        sys.path.insert(0, candidate)
                for candidate in extractedModulePaths:
                    if candidate not in sys.path:
                        sys.path.insert(0, candidate)

                package = types.ModuleType("burp")
                package.__package__ = "burp"
                package.__path__ = []
                sys.modules["burp"] = package

                def _load_submodule(name, source):
                    module = types.ModuleType(name)
                    module.__package__ = "burp"
                    exec(source, module.__dict__)
                    sys.modules[name] = module
                    setattr(package, name.rsplit(".", 1)[1], module)

                _load_submodule("burp.encoder", burpEncoderSource)
                _load_submodule("burp.crypto", burpCryptoSource)
                exec(burpInitSource, package.__dict__)
                """);
            context.eval("python", source);
            return ScriptRunResult.succeeded(text(stdout), text(stderr));
        } catch (IOException | RuntimeException e) {
            return ScriptRunResult.failed(text(stdout), text(stderr), e.toString());
        } finally {
            deleteRecursively(extractionRoot);
        }
    }

    @Override
    public void close() {
    }

    private static String text(ByteArrayOutputStream stream) {
        return stream.toString(StandardCharsets.UTF_8);
    }

    private static String loadResource(String relativePath) throws IOException {
        String resourcePath = BURP_RESOURCE_ROOT + "/" + relativePath.substring("burp/".length());
        try (InputStream stream = GraalPyPythonRuntime.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Missing Python wrapper resource " + resourcePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> extractedModulePaths(Path extractionRoot) {
        if (extractionRoot == null) {
            return List.of();
        }

        List<String> candidates = new ArrayList<>();
        candidates.add(extractionRoot.resolve("src").toString());
        candidates.add(extractionRoot.resolve("venv").resolve("Lib").resolve("site-packages").toString());
        candidates.add(extractionRoot.resolve("GRAALPY-VFS").resolve("com.pythonburp").resolve("burp-python-ide").resolve("src").toString());
        candidates.add(extractionRoot.resolve("GRAALPY-VFS").resolve("com.pythonburp").resolve("burp-python-ide").resolve("venv").resolve("Lib").resolve("site-packages").toString());
        return candidates;
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            Files.walk(root)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException ignored) {
        }
    }
}
