package com.pythonburp.python;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PythonRuntimeBootstrapTest {
    @Test
    void bootstrapsCompleteCompatibilityRuntimeAndRetainsDllHandle() {
        String command = PythonRuntimeBootstrap.pipBootstrapCommand();
        String marker = "bytes.fromhex('";
        int start = command.indexOf(marker) + marker.length();
        int end = command.indexOf("')", start);
        String source = new String(HexFormat.of().parseHex(command.substring(start, end)), StandardCharsets.UTF_8);

        assertTrue(source.contains("BURP_PYTHON_COMPAT_ROOT"));
        assertTrue(source.contains("python314.zip"));
        assertTrue(source.contains("_burp_dll_directory_handles.append"));
        assertFalse(source.contains("BURP_PYTHON_FALLBACK_STDLIB_ROOT"));
        assertFalse(source.contains("BURP_PYTHON_COMPAT_NATIVE_ROOT"));
    }
}
