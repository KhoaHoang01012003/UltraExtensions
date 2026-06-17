package com.pythonburp.python;

import com.pythonburp.bridge.BurpBridge;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CPythonWorkerRepeaterBridgeTest {
    @Test
    void pythonBurpRepeaterSendUsesJavaRepeaterBridge() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> url = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> tabName = new AtomicReference<>();
        BurpBridge bridge = new BurpBridge(
            (m, u, b) -> new com.pythonburp.bridge.HttpBridge.HttpResult(200, "ok"),
            (m, u, b, tab) -> {
                method.set(m);
                url.set(u);
                body.set(b);
                tabName.set(tab);
            }
        );
        try (PythonRuntime runtime = new CPythonRuntimeFactory().get(bridge)) {
            ScriptRunResult result = runtime.execute("""
                from burp import repeater
                repeater.send("POST", "https://target.example/repeater", "a=1&b=2", tab_name="Demo")
                print("done")
                """, Duration.ofSeconds(30));

            assertEquals(ScriptStatus.SUCCEEDED, result.status(), result.errorMessage() + "\nSTDERR:\n" + result.stderr());
            assertTrue(result.stdout().contains("done"));
        }
        assertEquals("POST", method.get());
        assertEquals("https://target.example/repeater", url.get());
        assertEquals("a=1&b=2", body.get());
        assertEquals("Demo", tabName.get());
    }
}
