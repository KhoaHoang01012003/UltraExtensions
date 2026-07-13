package com.pythonburp.python;

public final class PythonRuntimeBootstrap {
    public static final String ENV_RPC_DIR = "BURP_PYTHON_RPC_DIR";
    public static final String ENV_USER_PACKAGES = "BURP_PYTHON_USER_PACKAGES";
    public static final String ENV_HELPER_ROOT = "BURP_PYTHON_HELPER_ROOT";
    public static final String ENV_FALLBACK_STDLIB_ROOT = "BURP_PYTHON_FALLBACK_STDLIB_ROOT";
    public static final String ENV_COMPAT_NATIVE_ROOT = "BURP_PYTHON_COMPAT_NATIVE_ROOT";
    public static final String ENV_PIP_ROOT = "BURP_PYTHON_PIP_ROOT";

    private static final String COMMON_BOOTSTRAP = """
        import os
        import sys

        def _burp_prepend(path):
            if not path or not os.path.isdir(path):
                return
            normalized = os.path.normcase(os.path.abspath(path))
            for existing in sys.path:
                if existing and os.path.normcase(os.path.abspath(existing)) == normalized:
                    return
            sys.path.insert(0, path)

        for candidate in reversed([
            os.environ.get("%s", ""),
            os.environ.get("%s", ""),
            os.environ.get("%s", ""),
            os.environ.get("%s", ""),
            os.environ.get("%s", ""),
        ]):
            _burp_prepend(candidate)

        compat_native = os.environ.get("%s", "")
        if compat_native and hasattr(os, "add_dll_directory"):
            try:
                os.add_dll_directory(compat_native)
            except OSError:
                pass
        """.formatted(
        ENV_PIP_ROOT,
        ENV_FALLBACK_STDLIB_ROOT,
        ENV_COMPAT_NATIVE_ROOT,
        ENV_HELPER_ROOT,
        ENV_USER_PACKAGES,
        ENV_COMPAT_NATIVE_ROOT
    );

    private static final String RPC_BOOTSTRAP = """
        import builtins
        import pathlib
        import time
        import uuid

        rpc_dir = os.environ.get("%s", "")

        def _rpc(fields):
            if not rpc_dir:
                raise RuntimeError("%s is not configured")
            request_id = uuid.uuid4().hex
            root = pathlib.Path(rpc_dir)
            request = root / f"{request_id}.request"
            response = root / f"{request_id}.response"
            request.write_text(
                "\\n".join([*[f"{key}={value or ''}" for key, value in fields.items()], "__end=1", ""]),
                encoding="utf-8",
            )
            deadline = time.monotonic() + 3600
            while not response.exists():
                if time.monotonic() > deadline:
                    raise TimeoutError(f"Timed out waiting for Burp RPC response: {fields.get('operation', '')}")
                time.sleep(0.025)
            payload = {}
            for line in response.read_text(encoding="utf-8-sig").splitlines():
                key, _, value = line.partition("=")
                payload[key] = value.replace("\\\\n", "\\n").replace("\\\\r", "\\r")
            return payload

        def _burp_input(prompt=""):
            payload = _rpc({"operation": "stdin.read", "prompt": prompt or ""})
            if payload.get("ok") != "true":
                raise EOFError(payload.get("error", "Interactive input failed"))
            return payload.get("text", "")

        class _BurpStdin:
            encoding = "utf-8"

            def readline(self, *args, **kwargs):
                return _burp_input() + "\\n"

            def read(self, *args, **kwargs):
                return _burp_input()

            def readable(self):
                return True

            def isatty(self):
                return False

        if rpc_dir:
            builtins.input = _burp_input
            sys.stdin = _BurpStdin()
        """.formatted(ENV_RPC_DIR, ENV_RPC_DIR);

    private static final String EDITOR_LAUNCHER = COMMON_BOOTSTRAP + RPC_BOOTSTRAP + """
        import runpy
        import sys
        runpy.run_path(sys.argv[1], run_name="__main__")
        """;

    private static final String CUSTOM_COMMAND_LAUNCHER = COMMON_BOOTSTRAP + RPC_BOOTSTRAP + """
        import pathlib
        import runpy
        import sys

        args = sys.argv[1:]
        if not args:
            raise SystemExit("Custom command is empty. Enter the command tail after python.exe.")

        first = args[0]
        if first == "-m":
            if len(args) < 2:
                raise SystemExit("Custom command is missing the module name after -m.")
            module_name = args[1]
            sys.argv = [module_name, *args[2:]]
            runpy.run_module(module_name, run_name="__main__", alter_sys=True)
        elif first == "-c":
            if len(args) < 2:
                raise SystemExit("Custom command is missing the inline code after -c.")
            code = args[1]
            sys.argv = ["-c", *args[2:]]
            exec(compile(code, "<burp-python-custom-command>", "exec"), {"__name__": "__main__", "__file__": "<string>"})
        elif first.startswith("-"):
            raise SystemExit(
                "Unsupported interpreter flag in custom command mode: "
                + first
                + ". Use -m, -c, or a script path after python.exe."
            )
        else:
            script = pathlib.Path(first).resolve()
            sys.argv = [str(script), *args[1:]]
            runpy.run_path(str(script), run_name="__main__")
        """;

    private static final String PIP_BOOTSTRAP_SCRIPT = COMMON_BOOTSTRAP + """
        import runpy
        import sys
        sys.argv = ["pip", *sys.argv[1:]]
        runpy.run_module("pip", run_name="__main__", alter_sys=True)
        """;

    private static final String PIP_BOOTSTRAP = (
        "exec(compile(bytes.fromhex('%s').decode('utf-8'), "
            + "'<burp-python-pip-bootstrap>', 'exec'))"
    ).formatted(hex(PIP_BOOTSTRAP_SCRIPT));

    private PythonRuntimeBootstrap() {
    }

    public static String editorLauncher() {
        return EDITOR_LAUNCHER;
    }

    public static String customCommandLauncher() {
        return CUSTOM_COMMAND_LAUNCHER;
    }

    public static String pipBootstrapCommand() {
        return PIP_BOOTSTRAP;
    }

    private static String hex(String value) {
        StringBuilder builder = new StringBuilder(value.length() * 2);
        for (int index = 0; index < value.length(); index++) {
            builder.append(String.format("%02x", (int) value.charAt(index)));
        }
        return builder.toString();
    }
}
