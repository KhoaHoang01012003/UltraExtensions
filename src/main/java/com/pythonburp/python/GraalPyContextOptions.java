package com.pythonburp.python;

import java.util.Map;

final class GraalPyContextOptions {
    static final Map<String, String> DEFAULTS = Map.of(
        "engine.WarnInterpreterOnly", "false"
    );

    private GraalPyContextOptions() {
    }
}
