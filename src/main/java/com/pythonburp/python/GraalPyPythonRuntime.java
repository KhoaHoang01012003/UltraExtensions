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
import java.nio.charset.StandardCharsets;
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
            context.getBindings("python").putMember("burpBridge", bridge);
            context.getBindings("python").putMember("burpModuleRoot", fileSystem.getMountPoint() + "/src");
            context.getBindings("python").putMember("burpResourceModuleRoot", "/" + RESOURCE_DIRECTORY + "/src");
            context.getBindings("python").putMember("burpInitSource", loadResource("burp/__init__.py"));
            context.getBindings("python").putMember("burpEncoderSource", loadResource("burp/encoder.py"));
            context.getBindings("python").putMember("burpCryptoSource", loadResource("burp/crypto.py"));
            context.eval("python", """
                import builtins
                import sys
                import types

                builtins.burpBridge = burpBridge
                for candidate in (burpModuleRoot, burpResourceModuleRoot):
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
}
