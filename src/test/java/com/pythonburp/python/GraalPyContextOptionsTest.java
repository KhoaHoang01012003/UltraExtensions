package com.pythonburp.python;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GraalPyContextOptionsTest {
    @Test
    void disablesInterpreterOnlyWarningForHostJvmEmbedding() {
        assertEquals("false", GraalPyContextOptions.DEFAULTS.get("engine.WarnInterpreterOnly"));
    }
}
