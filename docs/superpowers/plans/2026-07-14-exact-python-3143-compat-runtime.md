# Exact Python 3.14.3 Compatibility Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Supply a complete CPython 3.14.3 runtime from `LOCALAPPDATA` while always launching Zenmap's fixed `python.exe`, including working SSL and pip over HTTPS.

**Architecture:** Build from the official 3.14.3 x64 embeddable archive instead of a locally installed Python. Stage the complete archive as one compatibility root, inject it through a retained DLL directory and deterministic `sys.path`, and fail closed when the Zenmap interpreter fingerprint or SSL/pip probe is incompatible.

**Tech Stack:** Java 21, Gradle, PowerShell, CPython 3.14.3 embeddable runtime, JUnit 6.

---

### Task 1: Define Exact Runtime Contract

**Files:**
- Modify: `build.gradle`
- Modify: `scripts/prepare-cpython-bundle.ps1`
- Test: `src/test/java/com/pythonburp/catalog/PackageSmokeTest.java`
- Test: `src/test/java/com/pythonburp/fatjar/IsolatedFatJarSmoke.java`

- [ ] Add build properties for compatibility version `3.14.3`, archive name, official URL, and SHA-256 digest.
- [ ] Remove `FallbackStdlibDir` and `CompatPythonRoot` inputs so bundle output cannot depend on local Python installations.
- [ ] Download and hash-check the official archive, then copy its complete extracted contents to `cpython/windows-x64/python-compat-3.14.3`.
- [ ] Run the smoke tests before production changes and confirm they fail when the stripped interpreter cannot resolve the new compatibility layout.

### Task 2: Bootstrap Complete Compatibility Root

**Files:**
- Modify: `src/main/java/com/pythonburp/python/PythonRuntimeBootstrap.java`
- Modify: `src/main/java/com/pythonburp/python/CPythonRuntimeFactory.java`
- Modify: `src/main/java/com/pythonburp/python/CPythonWorkerRuntime.java`
- Test: `src/test/java/com/pythonburp/python/CPythonRuntimeFactoryTest.java`
- Test: `src/test/java/com/pythonburp/python/CPythonWorkerRuntimeTest.java`

- [ ] Add failing tests asserting the compatibility root and `python314.zip` are exported to worker and pip environments.
- [ ] Add a failing test asserting the DLL-directory handle is retained for the lifetime of the bootstrap process.
- [ ] Replace separate fallback-stdlib/native-root fields with one complete compatibility root.
- [ ] Prepend user packages and Burp helpers first, followed by `python314.zip`, the compatibility root, and bundled pip.
- [ ] Keep the Zenmap executable unchanged for editor, interactive, custom-command, and pip invocations.

### Task 3: Enforce Fingerprint and SSL/Pip Readiness

**Files:**
- Modify: `src/main/java/com/pythonburp/python/PythonRuntimeEnvironment.java`
- Modify: `src/main/java/com/pythonburp/python/CPythonRuntimeFactory.java`
- Modify: `src/main/java/com/pythonburp/BurpPythonIdeExtension.java`
- Test: `src/test/java/com/pythonburp/python/CPythonRuntimeFactoryTest.java`

- [ ] Add failing tests for unsupported patch versions, non-Windows platforms, non-x64 architectures, and non-MSVC compiler fingerprints.
- [ ] Extend metadata probing to include `sys.version` and validate exact compatibility before staging native assets.
- [ ] Probe imports of `_socket`, `socket`, `_ssl`, `ssl`, `hashlib`, `select`, and `unicodedata`, create an SSL context, and run bundled pip `--version`.
- [ ] Disable package management and report the complete probe failure instead of marking pip available after a failed startup probe.

### Task 4: Reproducible End-to-End Verification

**Files:**
- Modify: `src/test/java/com/pythonburp/catalog/PackageSmokeTest.java`
- Modify: `src/test/java/com/pythonburp/fatjar/IsolatedFatJarSmoke.java`
- Modify: `build.gradle`

- [ ] Allow smoke tests to use a configured stripped Python 3.14.3 test directory rather than the polluted local Nmap installation.
- [ ] Verify HTTPS access and bundled pip wheel download using the staged compatibility runtime.
- [ ] Run `gradlew test`, `gradlew packageSmokeTest`, `gradlew fatJarIsolatedSmoke`, and `gradlew check fatJar` with zero failures.
- [ ] Inspect the JAR and confirm the complete 3.14.3 compatibility archive is present and no `C:\Program` or `C:\Program1` reference exists.

### Task 5: Publish

**Files:**
- Update: `D:\python-burp\extensions\burp-python-ide-enhanced-0.3.0-all.jar`

- [ ] Commit the source and documentation changes on `task/nmap-runtime-relocation`.
- [ ] Copy the verified fat JAR to the existing `extensions` path in the main repository.
- [ ] Commit and push `main` to `KhoaHoang01012003/UltraExtensions`.
- [ ] Report the source commit, published JAR commit, and verification commands.
