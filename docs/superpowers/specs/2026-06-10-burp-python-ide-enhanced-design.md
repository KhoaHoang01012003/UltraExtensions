# Burp Python IDE Enhanced Design

Date: 2026-06-10

## Goal

Build a serious Python IDE inside Burp Suite as a single Java extension JAR. The user installs only one JAR through Burp's Java extension loader. The extension must not require system Python, Jython, or a portable CPython folder.

The default architecture is Enhanced Mode: the JAR is the only installation artifact, but at runtime the extension may extract bundled runtime resources and native libraries into a controlled extension cache directory. This enables broader dependency support than a pure in-JAR mode while avoiding a separate portable Python installation.

## Non-Goals

- Do not ship or invoke `python.exe`.
- Do not require Python to be installed on the host.
- Do not promise full CPython or full PyPI compatibility.
- Do not use Jython as the main scripting engine.
- Do not require users to manually place dependency folders on disk.
- Do not run script execution, package extraction, package import checks, or long Burp API calls on Burp's UI thread.

## Platform Assumptions

Initial target:

- Burp Suite on Windows x64.
- Java 21 or lower, matching Burp extension requirements.
- Java extension using PortSwigger's Montoya API.

The design should keep room for Linux and macOS profiles, but Windows x64 is the first compatibility target because native package behavior depends heavily on OS and architecture.

## Architecture

```text
BurpPythonIDE.jar
+-- Extension bootstrap
|   +-- Montoya API entry point
|   +-- lifecycle management
|   +-- cache manager
|   +-- settings registration
|
+-- IDE UI
|   +-- project/script explorer
|   +-- multi-tab Python editor
|   +-- run/stop controls
|   +-- stdout/stderr console
|   +-- traceback viewer
|   +-- package catalog view
|   +-- snippet library
|   +-- Burp API object browser
|
+-- Python runtime
|   +-- embedded GraalPy
|   +-- isolated script contexts
|   +-- per-run globals
|   +-- permission profile
|   +-- import path manager
|
+-- Dependency system
|   +-- bundled pure-Python packages
|   +-- bundled GraalPy-compatible wheels/packages
|   +-- bundled native resource pack
|   +-- Java-backed replacement modules
|   +-- compatibility metadata
|
+-- Burp bridge
    +-- burp.http
    +-- burp.proxy
    +-- burp.repeater
    +-- burp.encoder
    +-- burp.crypto
    +-- burp.jwt
    +-- burp.parsers
    +-- burp.scanner_helpers
```

## Runtime Model

The extension starts as a normal Java Burp extension. On load, it initializes the IDE UI and prepares a cache root under an extension-controlled location. The cache path is configurable, but defaults to a Burp/user-data-safe location rather than a project folder.

On first use, the cache manager extracts bundled runtime assets from the JAR:

- GraalPy runtime resources required by the embedded engine.
- Bundled Python packages.
- Native libraries from the compatibility pack.
- Package metadata and import indexes.

Extraction is versioned by extension version, runtime version, OS, architecture, and package catalog hash. If the hash matches, the existing cache is reused. If the hash changes, a new cache directory is created and the old one is marked for cleanup.

The extension does not place files in `PATH`, does not write Python registry keys, does not register file associations, and does not create a standalone Python installation.

## Python Engine

Use GraalPy through the JVM polyglot APIs.

Each script run gets an isolated execution context with:

- A fresh globals dictionary.
- Full host access by default.
- A curated `sys.path`.
- Redirected `stdout` and `stderr`.
- A cancellation token.
- A configurable timeout.

The extension exposes Java-backed Python modules for convenience, but the default permission profile is intentionally high-trust. Scripts may use direct network access, filesystem access, OS process APIs, Java interop, and bundled native compatibility resources unless the user later disables those capabilities in settings.

Example user script:

```python
from burp import http, encoder, jwt

resp = http.send("GET", "https://target.example/api/me")
print(resp.status_code)
print(encoder.base64_decode("SGVsbG8="))
print(jwt.decode_without_verify(resp.headers.get("Authorization", "")))
```

## Execution And UI Threading

Burp is a Java GUI application. The extension must treat Swing's Event Dispatch Thread as UI-only. Script execution must never run directly on the UI thread because a long loop, slow import, package extraction step, or network call could freeze Burp.

Threading rules:

- The Montoya extension entry point registers UI components quickly and returns.
- Swing components are created and mutated only on the Event Dispatch Thread.
- The extension inherits Burp's current Look and Feel and must not call global Look and Feel installers such as `UIManager.setLookAndFeel(...)` or `FlatLaf.setup()`.
- GraalPy startup, package extraction, package smoke tests, script execution, import scanning, formatting, and long Burp API calls run on background executors.
- The UI never calls `Future.get()`, `Thread.sleep()`, `join()`, native extraction, package import, or script execution on the Event Dispatch Thread.
- Console output is buffered on a background queue and flushed to Swing in batches.
- Traceback highlighting and status updates are posted through `SwingUtilities.invokeLater` or an equivalent EDT dispatcher.
- Large outputs use a bounded console buffer so print-heavy scripts do not make the IDE or Burp sluggish.
- Proxy history reads and other potentially large Burp data operations are paginated or streamed into the UI.

Execution model:

```text
Run button on EDT
    -> create ScriptRunRequest
    -> submit to ScriptExecutor background pool
    -> start/update status on EDT
    -> run GraalPy context off EDT
    -> stream stdout/stderr to ConsoleEventQueue
    -> batch-render console events on EDT
    -> publish completion/error/cancelled state on EDT
```

The first release should allow one active run per editor tab. A global executor limit prevents several scripts from consuming all CPU at once. The Stop action cancels the script future, signals the run context, and closes or interrupts the GraalPy context where supported. If a Java-backed Burp operation cannot stop instantly, the UI remains responsive and shows a stopping state until the operation returns.

## Dependency Strategy

The extension uses a curated package catalog rather than arbitrary live `pip install` as the primary dependency mechanism.

Package tiers:

```text
Tier 1: Python stdlib and pure-Python packages
Tier 2: packages tested against embedded GraalPy
Tier 3: Java-backed replacement modules for native-heavy packages
Tier 4: native compatibility pack extracted from the JAR into cache
```

The package catalog includes metadata:

- Package name and version.
- Compatibility tier.
- Supported OS/architecture.
- Whether native extraction is required.
- Whether Java wrapper fallback exists.
- Known limitations.
- Smoke-test import command.

The IDE presents this catalog in a Package view. Users can see whether a package is bundled, active, native-backed, or unsupported.

## Native Dependency Handling

Enhanced Mode allows native resources by default.

Native resources are bundled inside the JAR and extracted into a versioned cache directory before use. This is required because Windows and the JVM generally need native libraries to exist as real files before loading them.

Rules:

- Bundled native resources are available by default.
- Native libraries are selected by OS and architecture.
- Hashes are verified before loading.
- Extracted native libraries are stored in a deterministic extension cache path.
- Cache cleanup is managed by the extension.
- User-supplied package/native paths are planned after the bundled catalog works; when added, they follow the same full-access model and are controlled by path configuration rather than sandbox restrictions.

This improves support for native-heavy packages, but it still does not make the environment equivalent to CPython.

## Package Coverage Plan

The first catalog should prioritize pentest scripting workflows rather than data-science completeness.

Preferred bundled packages and modules:

```text
HTTP and web:
- burp.http Java-backed wrapper
- urllib-compatible helpers
- requests compatibility if GraalPy-compatible enough
- httpx only if compatibility testing passes

Parsing:
- beautifulsoup4
- html5lib
- defusedxml
- xmltodict
- cssselect or parsel if compatible

Encoding and data handling:
- python-dateutil
- charset-normalizer
- idna
- tabulate
- rich
- colorama
- PyYAML if compatible, otherwise Java-backed YAML helper

Security:
- burp.crypto Java-backed wrapper
- burp.jwt Java-backed wrapper
- pyjwt if compatible
- passlib if compatible
- Java BouncyCastle-backed primitives where needed

Network:
- burp.http via Montoya as primary
- DNS through dnsjava-backed wrapper
- WebSocket through Java-backed wrapper

Data:
- csv/json from stdlib
- sqlite only if runtime support is proven
- dataframe-lite helper for common tabular output
```

Native-heavy packages:

```text
numpy:
- Optional compatibility target, not a core dependency.
- Include only if GraalPy plus native pack smoke tests pass.

pandas:
- Optional compatibility target.
- Include only if numpy compatibility is stable enough.
- Not required for the first useful release.

cryptography:
- Prefer Java-backed burp.crypto wrapper.
- Include Python package only if compatibility and native loading are stable.

lxml:
- Prefer pure-Python or Java-backed parser alternatives.
- Include only as a compatibility target if reliable.
```

## Java-Backed Python Modules

The extension should provide Python modules that feel natural to script authors while delegating hard or Burp-specific work to Java.

Examples:

```python
from burp import http

resp = http.send(
    method="POST",
    url="https://target.example/login",
    headers={"Content-Type": "application/json"},
    body='{"user":"admin"}',
)
```

```python
from burp import crypto

print(crypto.sha256_hex(b"abc"))
print(crypto.hmac_sha256_hex(b"key", b"message"))
```

```python
from burp import proxy

for item in proxy.history(limit=50):
    print(item.method, item.url, item.status_code)
```

The initial wrapper modules:

- `burp.http`: send requests through Burp/Montoya.
- `burp.proxy`: read proxy history.
- `burp.repeater`: send selected messages to Repeater.
- `burp.encoder`: base64, URL, hex, HTML, gzip helpers.
- `burp.crypto`: hash, HMAC, AES, RSA, random bytes.
- `burp.jwt`: decode, inspect, modify, resign where keys are provided.
- `burp.parsers`: JSON, XML, HTML convenience wrappers.
- `burp.ui`: prompt, selection, output helpers.

## IDE Experience

The first screen is the actual IDE, not a landing page.

Core UI:

- Left script explorer.
- Center multi-tab editor.
- Bottom console and traceback panel.
- Right optional package/API browser.
- Compact top toolbar with Run, Stop, Save, Format, and Settings actions.
- Non-blocking status bar for runtime startup, package extraction, script running, stopping, and cache operations.

Expected workflows:

- Create a new script from a template.
- Run selected script.
- See output and errors immediately.
- Click a traceback line to jump to the editor line.
- Insert snippets for common Burp tasks.
- Browse bundled packages and compatibility notes.
- Save scripts into extension storage.
- Export/import script bundles as files when allowed.
- Keep editing, switching tabs, scrolling output, and using Burp while scripts run in the background.

Storage:

- Default script storage is Burp user settings or extension-managed workspace storage.
- Project-bound storage can be added for scripts that should travel with a Burp project.
- File export is explicit, not required for normal use.

## Permissions And Safety

Scripts run under a default full-access permission profile. This is a deliberate design choice for a local pentest IDE where the user wants maximum compatibility and minimum friction.

Default allowed:

- Access curated `burp.*` modules.
- Send HTTP requests through Burp APIs.
- Read selected Burp data exposed by wrappers.
- Import bundled packages.
- Write to the IDE console.
- OS command execution.
- Direct arbitrary file access.
- Direct unrestricted network sockets.
- Arbitrary Java host access.
- Loading bundled native compatibility resources.
- Importing from configured user package directories when that feature is added.
- Running package code that uses subprocess, filesystem, network, or Java interop.

Optional restrictions:

- Disable OS command execution.
- Disable direct filesystem access outside extension storage.
- Disable direct network sockets and force traffic through Burp wrappers.
- Disable unrestricted Java interop.
- Disable user-supplied package directories.
- Disable native compatibility resources.

The first release defaults to maximum access. Restrictions are not the primary design path and should be implemented as settings only after the full-access workflow is working. Even in full-access mode, the extension still verifies bundled native resource hashes before extraction so cache corruption or packaging mistakes are caught early.

## Error Handling

Runtime errors:

- Show Python traceback in the console.
- Highlight the editor line when possible.
- Preserve stdout/stderr output before failure.

Import errors:

- Show package status from the catalog.
- Explain whether the package is unsupported, disabled, or failed native loading.
- Suggest Java-backed alternatives when available.

Native extraction errors:

- Show cache path.
- Show expected and actual hash where safe.
- Offer cache reset.
- Avoid partially loading a broken native pack.

Script cancellation:

- Stop future work and mark context cancelled.
- Warn if a Java-backed operation cannot be interrupted immediately.
- Keep Burp UI responsive.

UI responsiveness errors:

- Detect accidental UI-thread blocking in development builds where practical.
- Log long EDT tasks with operation name and duration.
- Keep package extraction and runtime initialization progress visible without modal blocking dialogs.
- If a background task fails, publish the error to the console/status area without freezing the IDE.

## Build And Packaging

Use Gradle to produce a shaded/fat JAR.

The JAR contains:

- Extension classes.
- Montoya compile-only references excluded from the final bundle when appropriate.
- GraalPy embedding dependencies.
- Python resource bundle.
- Package catalog metadata.
- Native compatibility resources.
- Snippet templates.

Build outputs:

```text
build/libs/burp-python-ide-enhanced-all.jar
```

The JAR should be loadable through:

```text
Burp > Extensions > Installed > Add > Java > Select JAR
```

## Testing Strategy

Automated tests:

- Unit tests for cache manager.
- Unit tests for package catalog resolution.
- Unit tests for Java-backed Python modules.
- Smoke tests for GraalPy context creation.
- Smoke tests for package imports.
- Smoke tests for native extraction and hash verification.
- Unit tests for console buffering and bounded output behavior.

Burp integration tests:

- Load extension in Burp.
- Open IDE tab.
- Run hello-world script.
- Run script that reads proxy history.
- Run script that sends HTTP through Burp.
- Validate stdout/stderr redirection.
- Validate traceback line mapping.
- Validate Stop on long-running script.
- Validate the UI remains responsive during runtime startup.
- Validate the UI remains responsive during native extraction.
- Validate the UI remains responsive during a long-running or print-heavy script.
- Validate Burp interaction is not blocked by script execution.

Package compatibility tests:

Each bundled package gets:

- `import package_name`.
- One minimal functional test.
- Version assertion.
- OS/architecture compatibility record.

Native package candidates such as `numpy`, `pandas`, `cryptography`, and `lxml` must pass smoke tests before being shown as supported.

## Release Criteria

First useful release:

- Single JAR installs in Burp.
- IDE tab works.
- GraalPy runs Python 3 scripts.
- Scripts can call Java-backed `burp.*` modules.
- Full-access permission mode is the default.
- Script execution runs on background workers, not Burp's UI thread.
- Console output is buffered and rendered without UI lag.
- Bundled pure-Python catalog imports successfully.
- Enhanced cache extraction works.
- Native pack integrity checks work.
- Unsupported packages fail with clear messages.

Not required for first release:

- Full PyPI compatibility.
- Stable pandas support.
- Stable numpy support.
- Arbitrary user `pip install`.
- Cross-platform native packs.

## Key Risks

Native package compatibility is the largest risk. A single-JAR artifact can carry native resources, but native-heavy Python packages may still depend on CPython ABI behavior that GraalPy does not fully replicate.

Burp classloader behavior is another risk. GraalPy and shaded dependencies may need careful packaging to avoid conflicts.

Runtime size is also a risk. Bundling GraalPy and dependencies will produce a large JAR. This is acceptable for the design, but build and load times should be measured early.

## Initial Decisions

- Implementation language: Java, because Burp's current extension examples and Montoya documentation are Java-first.
- Python engine candidate: GraalPy 25.0.3, pinned during implementation unless smoke tests show a blocker.
- UI toolkit: Swing inheriting Burp's host Look and Feel, RSyntaxTextArea for Python editing, and standard Swing split panes/tabs for layout.
- Responsiveness model: background `ExecutorService` for runtime, script, package, and Burp-data tasks; EDT only for Swing UI updates.
- Default Windows cache path: `%LOCALAPPDATA%\BurpPythonIDE\cache\<extension-version>-<catalog-hash>\`.
- First catalog scope: pentest scripting packages and Java-backed Burp helpers before data-science packages.
- Default permission mode: full access, including filesystem, direct network, OS process APIs, Java interop, and bundled native compatibility resources.
- User-supplied package import: planned after the bundled catalog works; when added, it follows the full-access default and is controlled by path configuration rather than strict sandboxing.

## Approved Direction

Proceed with Enhanced Mode as the default and only initial runtime mode:

```text
One installable JAR
No system Python
No portable CPython folder
Embedded GraalPy
Bundled dependency catalog
Controlled extraction cache for native resources
Java-backed pentest helper modules
Full-access scripting by default
Background execution so Burp's UI stays responsive
```

## References

- PortSwigger Burp extension creation docs: https://portswigger.net/burp/documentation/desktop/extend-burp/extensions/creating
- PortSwigger Burp extension loading docs: https://portswigger.net/burp/documentation/desktop/extend-burp/extensions/creating/loading-in-burp
- GraalPy JVM developer docs: https://www.graalvm.org/python/jvm-developers/
- GraalPy Python developer docs: https://www.graalvm.org/python/python-developers/docs/
