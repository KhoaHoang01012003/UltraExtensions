# Nmap Runtime Relocation Design

## Summary

This change moves the embedded CPython worker runtime out of `LOCALAPPDATA` and into the Nmap installation tree at `C:\Program Files (x86)\Nmap\zenmap\bin\BurpPythonIDE\cpython-worker`.

The extension will continue to use its bundled CPython 3.12.10 runtime and bundled native/python dependencies. Only the extraction target changes. User-managed data such as package installs, staging directories, logs, cache, and settings remain under the existing `BurpPythonIDE` data root in `LOCALAPPDATA`.

## Problem Statement

The current extension extracts the bundled CPython worker runtime to a path under:

- `D:\Users\<username>\AppData\Local\BurpPythonIDE\runtime\cpython-worker\cpython-3.12.10-popular-pypdf-rpc1`

On some target machines, executables launched from this location are blocked by AppLocker or similar policy controls. The user has identified that machines with Nmap installed allow execution from the Nmap installation tree, specifically:

- `C:\Program Files (x86)\Nmap\zenmap\bin`

The goal is to keep the extension behavior intact while relocating the runtime extraction root so that the embedded `python.exe` and all related runtime components execute from the Nmap directory tree instead of `LOCALAPPDATA`.

## Goals

- Always extract the embedded CPython worker runtime beneath `C:\Program Files (x86)\Nmap\zenmap\bin`.
- Preserve the current bundled CPython runtime version and dependency set.
- Preserve existing worker-launch behavior, RPC behavior, and `PYTHONPATH`/user package wiring.
- Keep package manager state, user-installed packages, logs, temp files, and settings under the existing extension data root in `LOCALAPPDATA`.
- Fail early with a clear error if the Nmap installation path is unavailable or not writable.
- Build a replacement extension `jar` after implementation.
- Install Nmap on the current machine and run a local compatibility test of the relocated runtime flow.

## Non-Goals

- Switching the extension to use Nmap's own `python.exe` as the main interpreter.
- Moving package storage, package staging, settings, or caches into `Program Files`.
- Introducing runtime fallback back to `LOCALAPPDATA`.
- Changing the bundled CPython version, package catalog, or worker RPC protocol.
- Refactoring unrelated storage or package-management behavior.

## Constraints

- The target path is fixed by user request: `C:\Program Files (x86)\Nmap\zenmap\bin`.
- Writing inside `Program Files` may require elevated rights or may fail on some machines; the extension must surface this clearly.
- AppLocker allow-listing may be path-sensitive. This design assumes that placing the bundled runtime beneath the Nmap tree is sufficient for the target environment.
- The current workspace appears to expose compiled artifacts (`bin`, `build`, `jar`) but not the tracked Java/Gradle source tree in the visible working directory. Implementation may require locating the real sources or reconstructing them from another local path before coding begins.

## Current Behavior

Based on inspection of the compiled classes:

- `ExtensionDataPaths.windowsDefault()` resolves the extension root to `LOCALAPPDATA\\BurpPythonIDE`.
- `CPythonRuntimeFactory` builds the CPython extraction root from `paths.runtimeRoot().resolve("cpython-worker")`.
- `CPythonBundleExtractor` extracts the runtime bundle into `cacheRoot/<runtimeId>` and writes a ready marker file after extraction.
- `CPythonWorkerRuntime` launches the extracted `python.exe`, writes temporary launcher/source files into the runtime work directory, and injects `BURP_PYTHON_USER_PACKAGES` plus `PYTHONPATH` pointing at the extension's user package directory.

This means the runtime extraction location is coupled to `ExtensionDataPaths.runtimeRoot()`, while user package state is already logically separate.

## Proposed Architecture

### 1. Dedicated Nmap Runtime Root Resolver

Introduce a dedicated resolver for the embedded CPython worker runtime root on Windows:

- Base path: `C:\Program Files (x86)\Nmap\zenmap\bin`
- Extension-owned subpath: `BurpPythonIDE\cpython-worker`

The final extracted runtime remains versioned:

- `C:\Program Files (x86)\Nmap\zenmap\bin\BurpPythonIDE\cpython-worker\cpython-3.12.10-popular-pypdf-rpc1`

This keeps the existing runtime version marker scheme intact and avoids placing files directly at the top of `zenmap\bin`.

### 2. Keep Extension Data Paths in LOCALAPPDATA

`ExtensionDataPaths` remains unchanged for:

- `userPackages()`
- `packageStagingRoot()`
- `packageRequests()`
- `packageSources()`
- `pipCache()`
- `temp()`
- `logs()`
- `settings()`

This avoids mixing mutable user/application state with the shared Nmap installation tree.

### 3. Reuse Existing Extractor and Worker Runtime

`CPythonBundleExtractor` should continue to:

- Extract the resource tree into `<runtimeRoot>/<runtimeId>`
- Reuse the existing ready-marker behavior
- Preserve the current safety checks on normalized resolved paths

`CPythonWorkerRuntime` should continue to:

- Launch the extracted bundled `python.exe`
- Use the existing worker launcher script
- Set `BURP_PYTHON_USER_PACKAGES`
- Prepend the `LOCALAPPDATA` package directory to `PYTHONPATH`

No worker protocol changes are needed.

### 4. Explicit Failure on Missing or Unwritable Nmap Path

The runtime factory should validate that the base Nmap directory exists before extraction begins. If the directory is missing or runtime subdirectories cannot be created, the extension should fail with a specific, actionable error that explains one of:

- Nmap is not installed at the required path
- The Nmap installation tree is not writable by the current process
- The embedded runtime could not be extracted under the Nmap tree

The design intentionally does not fall back to `LOCALAPPDATA`.

## File and Component Impact

Expected implementation touch points:

- `com.pythonburp.python.CPythonRuntimeFactory`
  - Replace the current use of `paths.runtimeRoot()` for CPython worker extraction.
  - Add or call a fixed Windows Nmap runtime root resolver.
  - Add validation and clear failure messages.

- Possibly `com.pythonburp.storage.ExtensionDataPaths`
  - Only if a helper is needed for shared path composition.
  - Avoid changing existing non-runtime storage methods unless truly necessary.

- Runtime tests around CPython extraction/launch
  - Update or add tests covering the relocated extraction root and failure conditions.

- Build and packaging files
  - Only as needed to rebuild the extension `jar`.

## Data Flow

At extension startup:

1. Construct standard extension data paths in `LOCALAPPDATA`.
2. Resolve the fixed Nmap runtime base at `C:\Program Files (x86)\Nmap\zenmap\bin\BurpPythonIDE\cpython-worker`.
3. Extract bundled CPython resources into `<nmap-runtime-root>/<runtimeId>` if the ready marker is absent.
4. Launch `<extracted-runtime>\python.exe`.
5. Continue to point user package imports at the `LOCALAPPDATA` package directory via environment variables.

## Error Handling

The implementation should distinguish these cases:

- Nmap base directory missing
  - Raise an `IllegalStateException` or similar runtime startup error with a message that Nmap is required at the fixed path.

- Runtime directory creation denied
  - Raise a startup error that mentions lack of write access under the Nmap tree.

- Resource extraction failure
  - Preserve the underlying `IOException` cause in the wrapped error.

- Runtime process launch failure
  - Preserve existing worker runtime error behavior.

No silent fallback should occur.

## Testing Strategy

### Unit/Integration Tests

Add or update tests to cover:

- `CPythonRuntimeFactory` resolves the runtime root under the Nmap installation tree.
- The final extracted runtime path still includes the current `runtimeId`.
- User package paths remain under `LOCALAPPDATA`/existing extension data root.
- Runtime startup fails clearly when the Nmap base directory does not exist.
- Runtime startup fails clearly when the runtime subdirectory cannot be created.

If existing tests already cover `CPythonBundleExtractor` and `CPythonWorkerRuntime`, keep those focused and add factory-level tests for the new path policy instead of broad rewrites.

### Local Validation

After implementation:

1. Install Nmap on the current machine.
2. Confirm the installation creates `C:\Program Files (x86)\Nmap\zenmap\bin`.
3. Load the rebuilt extension into Burp.
4. Verify the runtime extracts into:
   - `C:\Program Files (x86)\Nmap\zenmap\bin\BurpPythonIDE\cpython-worker\cpython-3.12.10-popular-pypdf-rpc1`
5. Verify `python.exe` in that extracted runtime launches successfully through the extension.
6. Verify a representative script still imports bundled and user-installed packages correctly.
7. Verify package state still lands under the extension data root in `LOCALAPPDATA`.

## Build Output

The final deliverable is a rebuilt Burp extension `all.jar` containing the unchanged bundled CPython resources and the updated runtime path logic.

The existing output naming suggests the target build artifact is one of:

- `burp-python-ide-enhanced-<version>-all.jar`

The implementation phase should confirm the exact current build command and artifact location from the real source/build configuration once the source tree is available.

## Risks

- Machines without Nmap at the exact expected path will no longer be able to start the runtime.
- Machines where Burp does not have write permission under the Nmap installation tree will fail at runtime preparation.
- If AppLocker rules only allow the exact Nmap-shipped executable and not child paths beneath `zenmap\bin`, relocating the extracted bundle under the Nmap tree may still be insufficient.
- Because the visible workspace currently lacks the tracked source/build files, implementation may be blocked until the actual source tree is located.

## Alternatives Considered

### Use Nmap's Own `python.exe`

Rejected for now. This would better match strict path-based allow rules but creates ABI and dependency compatibility risk with the extension's bundled runtime and native modules.

### Move All Extension State into the Nmap Tree

Rejected. This would unnecessarily relocate mutable state such as user packages, caches, requests, and logs into `Program Files`, increasing permissions and maintenance risk.

### Fallback to LOCALAPPDATA When Nmap Is Missing

Rejected by user requirement. The runtime must always target the Nmap tree.

## Open Implementation Blocker

Before coding starts, the real Java/Gradle source tree must be identified in the local environment. The visible workspace currently exposes built outputs and resources, but not the tracked source files needed for a clean implementation and rebuild.
