# Zenmap External Python Runtime Design

## Summary

This change replaces the extension's embedded CPython interpreter with the preinstalled interpreter at `C:\Program Files (x86)\Nmap\zenmap\bin\python.exe`.

The extension will keep mutable state under `LOCALAPPDATA\BurpPythonIDE`, including user-installed dependencies, runtime work files, logs, and settings. Standard-library modules and whatever is already bundled with the Zenmap Python 3.14 interpreter will continue to come from that interpreter. Third-party dependencies managed by the extension will be installed into a versioned package root under `LOCALAPPDATA` and injected through `PYTHONPATH`.

The same change also introduces an interactive execution mode so scripts can prompt for input at runtime instead of forcing users to hard-code parameter values into the script body.

## Problem Statement

The previous design attempted to relocate the bundled interpreter under the Nmap installation tree so the extension could still launch its own `python.exe`. That approach still required writes beneath `Program Files`, which fails on target machines where the extension does not have permission to provision files there.

The user has confirmed that target machines already ship a usable interpreter at:

- `C:\Program Files (x86)\Nmap\zenmap\bin\python.exe`

The extension therefore needs a new model:

- run scripts with the existing Zenmap Python executable
- keep extension-managed third-party packages in `LOCALAPPDATA`
- remain flexible across interpreter-provided stdlib/bundled modules and user-installed dependencies
- support interactive terminal-like input during script execution

## Goals

- Always use `C:\Program Files (x86)\Nmap\zenmap\bin\python.exe` as the interpreter on Windows.
- Stop extracting or provisioning an embedded interpreter for normal runtime execution.
- Keep extension-managed dependencies, work files, logs, and settings in `LOCALAPPDATA`.
- Separate package environments by interpreter version so Python 3.14 packages do not mix with old 3.12 state.
- Probe interpreter compatibility at startup and fail clearly when the external interpreter is missing or unusable.
- Add an interactive mode that allows runtime user input similar to a terminal session.
- Preserve the existing Burp HTTP bridge and non-interactive run flow.
- Rebuild the extension jar and refresh the published jar artifact.

## Non-Goals

- Supporting arbitrary external interpreter paths chosen by the user.
- Preserving binary compatibility for all native packages that previously worked on embedded CPython 3.12.
- Moving extension data into `Program Files`.
- Implementing a full PTY or terminal emulator.
- Supporting multiple simultaneous interactive script sessions.

## Constraints

- The external interpreter path is fixed by user requirement.
- The user has stated the target interpreter is Python 3.14.
- Some catalog entries, especially native packages such as `numpy`, may not be safe to install against a new interpreter ABI without validation.
- Burp extension UI is Swing-based and must remain responsive while waiting for runtime input.

## Current Behavior

- `CPythonRuntimeFactory` extracts bundled runtime resources and launches the extracted `python.exe`.
- `ExtensionDataPaths.userPackages()` is hard-coded to `packages/cpython-3.12-windows-x64`.
- `PipCommandFactory` installs packages into that root using `python -m pip install --target`.
- `CPythonWorkerRuntime` writes a launcher script, injects `BURP_PYTHON_USER_PACKAGES`, and prepends the package root to `PYTHONPATH`.
- The UI supports one-shot script execution only; there is no way to answer `input()` prompts during execution.

## Proposed Architecture

### 1. External Interpreter Runtime Factory

Replace bundled-interpreter extraction with a runtime factory that:

- resolves `C:\Program Files (x86)\Nmap\zenmap\bin\python.exe`
- verifies the file exists
- probes the interpreter to capture:
  - executable path
  - major/minor/micro version
  - platform
  - whether `pip` is invokable through `-m pip`
- exposes the resolved interpreter path to both script execution and package management

If the interpreter is missing or the probe fails, startup should abort with a clear message.

### 2. Interpreter-Aware Data Layout

User package storage must no longer be hard-coded to CPython 3.12.

The new package root format should be derived from the external interpreter, for example:

- `LOCALAPPDATA\BurpPythonIDE\packages\python-3.14-windows-x64`

Related runtime state that depends on interpreter behavior should live under `LOCALAPPDATA`, not `Program Files`.

This includes:

- temporary script files
- launcher/helper files
- interactive session state
- copied Python helper package assets

### 3. Python Helper Asset Staging

The extension's bundled Python-side helper package under `src/main/python-worker` must still be available to executed scripts.

At runtime the extension should stage these helper files into an owned location under `LOCALAPPDATA`, such as:

- `LOCALAPPDATA\BurpPythonIDE\runtime-assets\python-worker`

`CPythonWorkerRuntime` should prepend both:

- the staged helper asset directory
- the interpreter-versioned user package directory

to `PYTHONPATH`.

This preserves imports like `from burp import http`.

### 4. Compatibility Gate

At extension startup, before UI registration, run a lightweight probe command against the external interpreter.

The probe should validate:

- Python major version is `3`
- `python -m pip --version` succeeds
- the interpreter can import baseline modules the extension depends on for execution

The result should be logged and reused instead of probing repeatedly on every run.

If the interpreter is Python 3.14, that is acceptable. The extension should isolate package state by version and let pure-Python packages proceed.

### 5. Native Package Policy

Because the interpreter ABI has changed, extension-managed native package installs must be treated conservatively.

The first implementation should:

- allow pure-Python dependencies
- block installation of catalog entries marked `nativeRequired=true`
- surface a clear message explaining that native packages are currently disabled for the external interpreter mode until compatibility is validated

This avoids silent breakage from incompatible `.pyd` or `.dll` files.

### 6. Interactive Mode

Add an interactive execution mode that supports scripts calling `input()` or reading from `stdin`.

The runtime protocol should add a second RPC operation in addition to `http.send`:

- `stdin.read`

Flow:

1. The launched Python helper overrides `input()` behavior through `sys.stdin` redirection or a thin wrapper.
2. When the script requests input, the Python side writes a request file in the RPC directory with the prompt text.
3. The Java runtime notices the request, pauses execution flow, and asks the UI for user input.
4. The Swing UI displays the prompt and lets the user submit a response.
5. The Java runtime writes the response file and the script continues.

The initial UI can be lightweight:

- an `Interactive Mode` toggle in the editor toolbar
- when enabled, input prompts appear through a modal dialog or a dedicated console input field

The mode should support repeated prompts within a single run.

### 7. Script Execution Modes

Two modes should exist:

- standard mode
  - current behavior for fire-and-forget scripts
- interactive mode
  - same execution engine, but with stdin prompt handling enabled and UI affordances for entering responses

Non-interactive scripts should continue to work unchanged in either mode.

## Component Impact

- `CPythonRuntimeFactory`
  - stop extracting bundled interpreter
  - resolve and probe Zenmap Python
  - provide interpreter metadata and executable path

- `ExtensionDataPaths`
  - add interpreter-aware package path helpers
  - add runtime-asset/work directories that remain under `LOCALAPPDATA`

- `PackageManagerService`
  - use interpreter-aware package root
  - block native-required packages in external interpreter mode

- `CPythonWorkerRuntime`
  - launch external interpreter
  - stage Python helper assets
  - inject helper/package roots into `PYTHONPATH`
  - implement stdin RPC handling

- `BurpPythonIdeExtension`
  - run compatibility probe at startup
  - wire runtime metadata into package and UI services

- `BurpPythonIdeTab` and console/UI classes
  - add interactive mode toggle
  - collect and return user input during a running script

## Error Handling

- Missing `python.exe`
  - fail startup with a message that Zenmap Python is required at the fixed path

- Probe failure
  - fail startup with the exact failing probe step where possible

- `pip` missing
  - fail startup because package management and dependency bootstrapping require it

- Native package install requested
  - fail that package operation with a clear compatibility message

- Interactive prompt canceled by the user
  - terminate the script run and surface a cancellation/error message

## Testing Strategy

### Unit and Integration Tests

- runtime factory resolves fixed Zenmap interpreter path
- probe parser handles Python 3.14 metadata
- package path changes based on interpreter version
- worker runtime prepends helper assets and user package root to `PYTHONPATH`
- native-required package requests are blocked in external interpreter mode
- interactive prompt RPC round-trip succeeds for one or more prompts
- non-interactive execution still succeeds unchanged

### Manual Validation

1. Ensure `C:\Program Files (x86)\Nmap\zenmap\bin\python.exe` exists.
2. Load the rebuilt extension into Burp.
3. Confirm startup logs the resolved interpreter path and version.
4. Run a simple script that imports stdlib modules only.
5. Install a pure-Python package and verify it lands in `LOCALAPPDATA`.
6. Run a script that imports that package.
7. Run a script that uses `input()` and confirm repeated prompts work.
8. Attempt a native package install such as `numpy` and confirm the extension blocks it with a clear message.

## Risks

- Some machines may ship a Python 3.14 environment with missing `pip` or unexpected policy restrictions.
- Package behavior can vary between target machines if the external interpreter includes different preinstalled components.
- Interactive mode built on request/response files is simpler than a PTY but may not satisfy every advanced terminal behavior.

## Alternatives Considered

### Continue provisioning beneath `Program Files`

Rejected because target machines may allow execution from the path but not allow the extension to write there.

### Best-effort external interpreter with no compatibility gate

Rejected because it would fail too late and too opaquely, especially with Python 3.14 package differences.

### Full terminal emulation

Rejected for the first implementation because it is much more complex than needed to support `input()`-style workflows.
