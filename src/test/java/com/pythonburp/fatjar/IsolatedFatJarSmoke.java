package com.pythonburp.fatjar;

import java.nio.file.Files;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.jar.JarFile;

public final class IsolatedFatJarSmoke {
    private IsolatedFatJarSmoke() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected fat JAR path");
        }

        Path jar = Path.of(args[0]).toAbsolutePath().normalize();
        Path tempRoot = Files.createTempDirectory("burp-python-fatjar-smoke");
        Path nmapBin = Path.of(System.getProperty("burpPythonTestZenmapBin")).toAbsolutePath().normalize();
        if (!Files.isRegularFile(nmapBin.resolve("python.exe"))) {
            throw new IllegalStateException("Zenmap Python is required at " + nmapBin + " for the isolated fat JAR smoke test.");
        }
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            requireEntry(jarFile, "cpython/windows-x64/python-compat-3.14.3/python314.zip");
            requireEntry(jarFile, "cpython/windows-x64/python-compat-3.14.3/_ssl.pyd");
            requireEntry(jarFile, "cpython/windows-x64/python-compat-3.14.3/_hashlib.pyd");
            requireEntry(jarFile, "cpython/windows-x64/python-compat-3.14.3/libssl-3-x64.dll");
            requireEntry(jarFile, "cpython/windows-x64/python-compat-3.14.3/libcrypto-3-x64.dll");
            if (jarFile.getEntry("cpython/windows-x64/python-compat-3.14.3/python.exe") != null) {
                throw new AssertionError("Compatibility runtime must not bundle an AppData python.exe");
            }
            rejectEntry(jarFile, "cpython/windows-x64/python-compat-3.14.3/libssl-3.dll");
            rejectEntry(jarFile, "cpython/windows-x64/python-compat-3.14.3/libcrypto-3.dll");
            rejectEntry(jarFile, "cpython/windows-x64/python.exe");
            rejectEntry(jarFile, "cpython/windows-x64/pythonw.exe");
            rejectEntry(jarFile, "cpython/windows-x64/python312.dll");
        }
        Path extensionRoot = tempRoot.resolve("localappdata/BurpPythonIDE");
        try (URLClassLoader loader = new URLClassLoader(
            new URL[]{jar.toUri().toURL()},
            ClassLoader.getPlatformClassLoader()
        )) {
            Class<?> extensionDataPathsClass =
                Class.forName("com.pythonburp.storage.ExtensionDataPaths", true, loader);
            Class<?> factoryClass = Class.forName("com.pythonburp.python.CPythonRuntimeFactory", true, loader);
            Object extensionDataPaths =
                extensionDataPathsClass.getConstructor(Path.class).newInstance(extensionRoot);
            Object factory =
                factoryClass
                    .getConstructor(Path.class, extensionDataPathsClass)
                    .newInstance(nmapBin, extensionDataPaths);
            Object runtime = factoryClass.getMethod("get").invoke(factory);
            try {
                Class<?> runtimeClass = runtime.getClass();
                Method execute = runtimeClass.getMethod("execute", String.class, Duration.class);

                Object result = execute.invoke(
                    runtime,
                    "import _socket, colorsys, logging.handlers, ssl, sys, unicodedata, urllib.request\n"
                        + "from burp import crypto\n"
                        + "print(sys.executable)\n"
                        + "print(colorsys.__name__)\n"
                        + "print(ssl.OPENSSL_VERSION)\n"
                        + "print(crypto.sha256_hex(b'abc'))\n"
                        + "with urllib.request.urlopen('https://pypi.org/simple/pip/', timeout=20) as response:\n"
                        + "    print(response.status)",
                    Duration.ofSeconds(60)
                );
                Object status = result.getClass().getMethod("status").invoke(result);
                Object stdout = result.getClass().getMethod("stdout").invoke(result);
                Object error = result.getClass().getMethod("errorMessage").invoke(result);
                if (!"SUCCEEDED".equals(status.toString())) {
                    throw new AssertionError("Fat JAR smoke failed: " + error);
                }
                if (!stdout.toString().contains(nmapBin.resolve("python.exe").toString())
                    || !stdout.toString().contains("colorsys")
                    || !stdout.toString().contains("OpenSSL")
                    || !stdout.toString().contains("ba7816bf")
                    || !stdout.toString().contains("200")) {
                    throw new AssertionError("Fat JAR smoke stdout missing expected output: " + stdout);
                }
            } finally {
                runtime.getClass().getMethod("close").invoke(runtime);
            }
        }
    }

    private static void requireEntry(JarFile jarFile, String name) {
        if (jarFile.getEntry(name) == null) {
            throw new AssertionError("Fat JAR is missing compatibility runtime entry: " + name);
        }
    }

    private static void rejectEntry(JarFile jarFile, String name) {
        if (jarFile.getEntry(name) != null) {
            throw new AssertionError("Fat JAR contains unused legacy interpreter payload: " + name);
        }
    }
}
