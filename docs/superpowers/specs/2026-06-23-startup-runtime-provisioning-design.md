# Startup Runtime Provisioning Design

## Goal

When the Burp extension loads on Windows, it must verify that the embedded CPython runtime can be extracted under `C:\Program Files (x86)\Nmap\zenmap\bin\BurpPythonIDE`. If the current user lacks write access, the extension should immediately offer an administrator-elevation flow, retry the writable check after provisioning, and continue initialization only if the runtime location becomes writable.

## Requirements

1. The check happens during extension startup, before the Python IDE tab is registered.
2. The writable probe targets the fixed Nmap runtime tree, not `LOCALAPPDATA`.
3. If the target directory is already writable, startup proceeds without prompting.
4. If the target directory is not writable, the extension shows a Swing confirmation dialog explaining that administrator permission is required.
5. If the user accepts, the extension launches an elevated helper that:
   - creates `C:\Program Files (x86)\Nmap\zenmap\bin\BurpPythonIDE` if needed
   - grants the built-in Users group modify access on that directory and its children
6. After the helper finishes, the extension retries the writable probe.
7. If the retry succeeds, normal startup continues.
8. If the user cancels or the helper fails, the extension does not register the Python IDE tab or runtime-backed services, and it logs a clear error.
9. The existing fixed runtime path remains unchanged.

## Design

Add a small startup provisioning workflow in the `com.pythonburp.python` package so it can reuse the fixed Nmap path policy directly. The workflow has three responsibilities:

- probe whether `...\zenmap\bin\BurpPythonIDE` is writable by creating the directory if possible, writing a temporary file, then deleting the probe file
- prompt the user on the Swing EDT when administrator approval is needed
- launch a PowerShell-based elevated helper through `Start-Process -Verb RunAs`, wait for it to finish, and then retry the probe

`BurpPythonIdeExtension.initialize(...)` will run this workflow before it creates `ExtensionContext`, `CPythonRuntimeFactory`, package services, or the UI tab. If the workflow returns a non-ready result, startup exits early after logging the failure reason.

## Testing

Add tests for:

- writable probe success without prompting
- access-denied probe path that prompts, provisions, retries, and succeeds
- declined prompt path that prevents suite tab registration
- provisioning failure path that prevents suite tab registration
- PowerShell command builder / workflow behavior without requiring real elevation
