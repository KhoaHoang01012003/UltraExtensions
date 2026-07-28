package com.pythonburp.python;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PythonCommandLineParserTest {
    @Test
    void parsesModuleCommandWithFlags() throws Exception {
        assertEquals(List.of("-m", "abc", "-h", "xyz"),
            PythonCommandLineParser.parseTail("-m abc -h xyz"));
    }

    @Test
    void preservesQuotedArguments() throws Exception {
        assertEquals(List.of("-m", "tool", "--name", "hello world"),
            PythonCommandLineParser.parseTail("-m tool --name \"hello world\""));
    }

    @Test
    void rejectsBlankCommands() {
        assertThrows(IOException.class, () -> PythonCommandLineParser.parseTail("   "));
    }

    @Test
    void rejectsLeadingPythonExecutable() {
        assertThrows(IOException.class, () -> PythonCommandLineParser.parseTail("python.exe -m abc"));
    }
}
