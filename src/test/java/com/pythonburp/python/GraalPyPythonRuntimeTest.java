package com.pythonburp.python;

import com.pythonburp.bridge.BurpBridge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GraalPyPythonRuntimeTest {
    @Test
    void evaluatesSimplePythonExpression() throws Exception {
        try (PythonRuntime runtime = new GraalPyPythonRuntime(new BurpBridge())) {
            ScriptRunResult result = runtime.execute("print(1 + 2)");

            assertEquals(ScriptStatus.SUCCEEDED, result.status(), result.errorMessage() + "\nSTDERR:\n" + result.stderr());
            assertTrue(result.stdout().contains("3"));
        }
    }

    @Test
    void exposesJavaBackedBurpModules() throws Exception {
        try (PythonRuntime runtime = new GraalPyPythonRuntime(new BurpBridge())) {
            ScriptRunResult result = runtime.execute("from burp import crypto\nprint(crypto.sha256_hex(b'abc'))");

            assertEquals(ScriptStatus.SUCCEEDED, result.status(), result.errorMessage() + "\nSTDERR:\n" + result.stderr());
            assertTrue(result.stdout().contains("ba7816bf8f01cfea"));
        }
    }
}
