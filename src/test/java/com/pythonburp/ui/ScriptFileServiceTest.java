package com.pythonburp.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ScriptFileServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsUtf8PythonScript() throws Exception {
        Path script = tempDir.resolve("demo.py");
        String source = "print('xin chao')\nprint('du lieu unicode')\n";

        ScriptFileService.save(script, source);

        assertEquals(source, ScriptFileService.load(script));
    }
}
