# Burp Python IDE Hybrid Package Manager Design

Date: 2026-06-12

## Goal

Add a package manager inside the Burp Python IDE so users can install Python packages without rebuilding the extension JAR or installing system Python.

The package manager uses the embedded CPython runtime and supports packages from PyPI, local wheel files, and `requirements.txt`. Installed packages persist across Burp and Windows restarts, remain visible to the user, and can be removed individually or erased together with all extension-owned Local AppData.

## Approved Decisions

- Use a persistent package environment shared by all Burp projects for the current Windows user.
- User-installed packages take precedence over packages bundled in the JAR.
- Disable install, upgrade, uninstall, and cleanup actions while any Python script is running.
- Support PyPI, local `.whl` files, and local `requirements.txt` files in the first release.
- Let pip resolve dependencies and show its warnings and conflicts in the Package Manager log.
- Use pip/system networking by default, with configurable proxy and package index settings.
- Uninstall removes only the user-installed copy. A bundled copy becomes active again automatically.
- Present Package Manager as a separate workspace tab next to the editor workspace.
- Provide `Clear User Packages`, `Clear pip Cache`, and `Reset All Extension Data` actions.

## Storage Model

The extension owns one data root:

```text
%LOCALAPPDATA%\BurpPythonIDE\
+-- runtime\
|   +-- <runtime-id>\
+-- packages\
|   +-- cpython-3.12-windows-x64\
+-- pip-cache\
+-- temp\
+-- logs\
+-- settings\
+-- delete-pending-*\
```

This is shared across Burp projects and Burp restarts for the current Windows account. It is not shared across separate Windows users.

Files explicitly saved through `Save As` outside this root are user files and must never be deleted by extension cleanup.

## Package Precedence

Every new worker process receives the user package directory before bundled `site-packages` on `sys.path`.

```text
1. %LOCALAPPDATA%\BurpPythonIDE\packages\cpython-3.12-windows-x64
2. bundled CPython site-packages extracted from the JAR
3. Python standard library
```

Installing a different version of a bundled package overrides the bundled version for subsequent script runs. Removing the user copy restores the bundled version without rebuilding or reloading the JAR.

Package changes apply only to workers started after the operation completes. No worker may remain active during a mutating package operation.

## Installation Sources

### PyPI

Accept a package requirement such as:

```text
pypdf
pypdf==6.13.2
requests>=2.34,<3
```

Run the embedded interpreter using argument-safe process construction:

```text
python.exe -m pip install --upgrade --target <user-package-dir> --cache-dir <pip-cache> <requirement>
```

The requirement is passed as one process argument, never concatenated into a shell command.

### Local Wheel

Use a file chooser restricted to `.whl`, then pass the selected absolute path directly to pip. Wheels must match CPython 3.12, Windows x64, or a compatible pure-Python wheel tag.

### Requirements File

Use a file chooser for `requirements.txt`, then run pip with `-r <absolute-path>`. Relative references inside the requirements file are resolved according to pip behavior.

Source directory installation is not included in the first release. Pip may still select a source distribution from PyPI when no compatible wheel exists; the UI must warn that source builds can execute package build code and may fail because build tools are not bundled.

## Networking Settings

Default behavior uses pip and Windows network settings. Package Manager settings support:

- Index URL.
- Extra index URL.
- Proxy URL.
- Trusted hosts.
- Network timeout.

Secrets embedded in proxy URLs must not be written to the console or normal logs. Settings are stored under the extension data root and are deleted by `Reset All Extension Data`.

## Package Inventory

The Package Manager obtains inventory from the embedded interpreter using `importlib.metadata`. Each row shows:

- Distribution name.
- Active version.
- Source: `User cache` or `Bundled`.
- Bundled fallback version when one exists.
- Whether native files such as `.pyd` or `.dll` are present.
- Available actions: Upgrade or Uninstall.

Inventory refresh runs in a background executor. Swing components are updated only on the Event Dispatch Thread.

## User Interface

The IDE workspace contains two top-level tabs:

```text
Editor | Package Manager
```

The Package Manager tab contains:

- PyPI requirement input.
- Install button.
- Install Wheel button.
- Install Requirements button.
- Refresh button.
- Settings button.
- Installed package table.
- Dedicated pip output panel.
- Storage usage summary.
- Clear User Packages button.
- Clear pip Cache button.
- Reset All Extension Data button.

During a package operation, the initiating controls are disabled, progress is shown without a modal dialog, and pip output is streamed in bounded batches to avoid blocking Burp's UI thread.

## Concurrency Rules

Use one package mutation executor so only one install, upgrade, uninstall, or cleanup operation runs at a time.

The extension maintains a shared activity coordinator:

- A script run cannot start while a package mutation or full reset is active.
- A package mutation cannot start while any script worker is active.
- Inventory refresh may run when scripts are idle and must not overlap a mutation.
- UI actions check state before submitting work and remain disabled until completion.
- No pip process, filesystem traversal, size calculation, or deletion runs on Swing's Event Dispatch Thread.

## Uninstall Behavior

Pip does not reliably uninstall distributions installed with a shared `--target` directory without environment ambiguity. The extension therefore manages user package ownership using installation manifests.

After each successful install, inventory records the files belonging to the affected distributions. Uninstall removes only files owned by the selected user-installed distributions, then refreshes metadata. Shared dependency files are retained when another installed distribution still references them where ownership can be determined.

If ownership is ambiguous, the UI offers a repair action that rebuilds the user package directory from a saved requirements snapshot rather than deleting arbitrary files.

## Cleanup Controls

### Clear User Packages

- Requires no active script or package operation.
- Stops any idle package helper process.
- Renames the user package directory to a uniquely named tombstone.
- Creates a fresh empty user package directory.
- Deletes the tombstone in the background.
- Leaves the bundled runtime, settings, and explicitly saved scripts untouched.

### Clear pip Cache

- Deletes only `%LOCALAPPDATA%\BurpPythonIDE\pip-cache`.
- Recreates an empty cache directory after deletion.

### Reset All Extension Data

- Requires explicit confirmation describing the exact data root.
- Stops all extension-owned workers and package processes.
- Closes streams and releases handles.
- Renames `%LOCALAPPDATA%\BurpPythonIDE` to a sibling `BurpPythonIDE.delete-pending-<timestamp>` directory where possible.
- Recreates only the minimum directory required to report completion.
- Deletes the tombstone recursively in the background.
- Requires the user to reload the extension before running scripts again.
- Never deletes files outside the extension-owned root.

On extension startup, stale `BurpPythonIDE.delete-pending-*` directories are deleted before runtime preparation. If Windows still locks native files, cleanup is retried at the next startup and the remaining path is shown to the user.

## Safety Model

Installing a Python package is equivalent to trusting and running third-party code. The Package Manager must show this warning near installation controls without blocking normal use.

Additional rules:

- Never execute pip through `cmd.exe`, PowerShell, or a shell string.
- Validate that cleanup targets remain under the expected Local AppData parent before recursive deletion.
- Do not automatically import newly installed packages as part of inventory.
- Do not load native package files into the Burp JVM.
- Native modules load only inside separate CPython worker processes.
- Preserve complete pip exit status and sanitized stdout/stderr for troubleshooting.
- A failed install must not modify the bundled runtime.

## Failure Handling

- Network failure: show pip output, exit code, index host, and retry action.
- Dependency conflict: keep pip's resolver output and refresh inventory to show the resulting environment.
- Incompatible wheel: explain the expected CPython 3.12 Windows x64 compatibility.
- Partial install: mark the environment as needing repair and offer rebuild from the saved requirements snapshot.
- Locked native files: move removable files into a tombstone and retry deletion on next startup.
- Disk-full failure: stop the operation, retain logs, and avoid replacing the previous usable package environment.
- Cancellation: terminate the pip process tree and refresh inventory before enabling controls again.

## Components

```text
PackageManagerPanel
    -> PackageManagerController
        -> PackageManagerService
            -> EmbeddedPipRunner
            -> SharedPackageEnvironment
            -> PackageInventoryReader
            -> ExtensionDataCleaner
        -> RuntimeActivityCoordinator
```

- `PackageManagerPanel`: Swing presentation and user actions.
- `PackageManagerController`: UI state, background task submission, and result publication.
- `PackageManagerService`: install, upgrade, uninstall, requirements, and repair workflows.
- `EmbeddedPipRunner`: safe process arguments, output streaming, timeout, and cancellation.
- `SharedPackageEnvironment`: paths, package precedence, manifests, and requirements snapshot.
- `PackageInventoryReader`: distribution metadata and bundled fallback comparison.
- `ExtensionDataCleaner`: bounded cleanup and tombstone retry handling.
- `RuntimeActivityCoordinator`: mutual exclusion between scripts and package mutations.

## Testing Strategy

Unit tests:

- Path confinement for every cleanup action.
- Package directory precedence in worker `sys.path`.
- Safe pip argument construction for PyPI, wheel, and requirements sources.
- Proxy/index setting sanitization.
- Inventory merge between user and bundled distributions.
- Script/package activity mutual exclusion.
- Tombstone cleanup and locked-file retry state.
- Bounded pip log buffering.

Integration tests:

- Install a small pure-Python package from a local test index or wheel fixture.
- Override a bundled package and verify the user version is imported.
- Remove the override and verify bundled fallback is restored.
- Install and import a compatible native wheel fixture.
- Install from `requirements.txt`.
- Cancel a long-running pip operation and verify its process tree exits.
- Clear user packages without deleting runtime or external `.py` files.
- Reset all extension data and verify the runtime is re-extracted after reload.
- Confirm Burp's UI remains responsive during install, inventory, size calculation, and cleanup.

Manual Burp verification:

- Install from PyPI.
- Install a local wheel.
- Install requirements.
- Run a script importing the installed package.
- Confirm controls are disabled while scripts run.
- Confirm persistence after Burp restart.
- Clear packages and verify bundled fallback.
- Reset all data and verify no extension-owned Local AppData remains after reload cleanup.

## Release Criteria

- Users can install from PyPI, wheel, and requirements without rebuilding the JAR.
- User packages persist across restarts and override bundled packages.
- Package mutations never overlap active scripts.
- Native wheels remain isolated to CPython worker processes.
- Package inventory and pip output are visible and responsive.
- User packages, pip cache, and all extension data can be removed through explicit controls.
- Cleanup cannot escape the extension-owned Local AppData root.
- Existing editor, Run, Stop, Load, Save As, and Clear Log workflows continue to work.

## Non-Goals

- System-wide installation visible to other Python installations.
- Sharing packages across Windows user accounts.
- Per-project virtual environments in the first release.
- Installing arbitrary local source directories in the first release.
- Guaranteeing that every PyPI package has a compatible Windows CPython 3.12 wheel.
- Sandboxing third-party Python packages after installation.
