package com.pythonburp.fatjar;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.time.Duration;

public final class IsolatedFatJarSmoke {
    private IsolatedFatJarSmoke() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected fat JAR path");
        }

        Path jar = Path.of(args[0]).toAbsolutePath().normalize();
        try (URLClassLoader loader = new URLClassLoader(
            new URL[]{jar.toUri().toURL()},
            ClassLoader.getPlatformClassLoader()
        )) {
            Class<?> factoryClass = Class.forName("com.pythonburp.python.CPythonRuntimeFactory", true, loader);
            Object factory = factoryClass.getConstructor().newInstance();
            Object runtime = factoryClass.getMethod("get").invoke(factory);
            Class<?> runtimeClass = runtime.getClass();
            Method execute = runtimeClass.getMethod("execute", String.class, Duration.class);

            Object result = execute.invoke(runtime, "import jwt\nprint('hello isolated cpython fat jar')", Duration.ofSeconds(30));
            Object status = result.getClass().getMethod("status").invoke(result);
            Object stdout = result.getClass().getMethod("stdout").invoke(result);
            Object error = result.getClass().getMethod("errorMessage").invoke(result);
            if (!"SUCCEEDED".equals(status.toString())) {
                throw new AssertionError("Fat JAR smoke failed: " + error);
            }
            if (!stdout.toString().contains("hello isolated cpython fat jar")) {
                throw new AssertionError("Fat JAR smoke stdout missing expected output: " + stdout);
            }
        }
    }
}
