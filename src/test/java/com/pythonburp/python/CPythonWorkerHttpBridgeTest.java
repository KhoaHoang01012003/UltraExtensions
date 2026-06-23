package com.pythonburp.python;

import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.bridge.HttpBridge;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CPythonWorkerHttpBridgeTest {
    @Test
    void pythonBurpHttpSendUsesJavaHttpBridge() throws Exception {
        BurpBridge bridge = new BurpBridge((method, url, body) -> {
            assertEquals("POST", method);
            assertEquals("https://target.example/api", url);
            assertEquals("payload", body);
            return new HttpBridge.HttpResult(202, "accepted");
        });
        try (PythonRuntime runtime = new CPythonRuntimeFactory().get(bridge)) {
            ScriptRunResult result = runtime.execute("""
                from burp import http
                resp = http.send("POST", "https://target.example/api", "payload")
                print(resp.status_code)
                print(resp.body)
                """, Duration.ofSeconds(30));

            assertEquals(ScriptStatus.SUCCEEDED, result.status(), result.errorMessage() + "\nSTDERR:\n" + result.stderr());
            assertTrue(result.stdout().contains("202"));
            assertTrue(result.stdout().contains("accepted"));
        }
    }
}
