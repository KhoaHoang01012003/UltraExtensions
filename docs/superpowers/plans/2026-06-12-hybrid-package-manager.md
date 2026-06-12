# Hybrid Package Manager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a persistent, controllable Package Manager workspace that installs PyPI requirements, local wheels, and requirements files into an extension-owned CPython environment without rebuilding the JAR.

**Architecture:** Introduce one extension data-root model, a shared script/package activity coordinator, and a transactional package environment. Package mutations build a staging directory with embedded pip and atomically swap it into the active user package directory only after success; new CPython workers prepend that active directory to `sys.path`. A Swing Package Manager workspace delegates all pip, inventory, sizing, and cleanup work to the existing background executors.

**Tech Stack:** Java 21, Swing, Gradle, embedded CPython 3.12, pip, `importlib.metadata`, JUnit 6, Montoya API.

---

## File Structure

New production files:

- `src/main/java/com/pythonburp/storage/ExtensionDataPaths.java`: authoritative Local AppData paths and path-confinement checks.
- `src/main/java/com/pythonburp/storage/ExtensionDataCleaner.java`: tombstone cleanup, package clearing, pip-cache clearing, and full reset.
- `src/main/java/com/pythonburp/concurrency/RuntimeActivityCoordinator.java`: mutual exclusion between scripts and package mutations.
- `src/main/java/com/pythonburp/packages/PackageManagerSettings.java`: pip index/proxy/trusted-host/timeout settings.
- `src/main/java/com/pythonburp/packages/PackageSettingsStore.java`: persistent settings under the extension data root.
- `src/main/java/com/pythonburp/packages/PipCommandFactory.java`: argument-safe embedded pip commands.
- `src/main/java/com/pythonburp/packages/PipRunResult.java`: pip exit status and sanitized output.
- `src/main/java/com/pythonburp/packages/EmbeddedPipRunner.java`: process execution, streaming, cancellation, and process-tree termination.
- `src/main/java/com/pythonburp/packages/PackageRequest.java`: requested PyPI/wheel/requirements source model.
- `src/main/java/com/pythonburp/packages/PackageRequestStore.java`: persistent top-level package requests used for transactional rebuilds.
- `src/main/java/com/pythonburp/packages/PackageInventoryEntry.java`: merged active/bundled inventory row.
- `src/main/java/com/pythonburp/packages/PackageInventoryReader.java`: `importlib.metadata` inventory and bundled fallback merge.
- `src/main/java/com/pythonburp/packages/SharedPackageEnvironment.java`: staging, swap, rollback, and environment paths.
- `src/main/java/com/pythonburp/packages/PackageManagerService.java`: install, upgrade, uninstall, refresh, clear, reset, and repair workflows.
- `src/main/java/com/pythonburp/ui/PackageManagerPanel.java`: dedicated Package Manager workspace.
- `src/main/java/com/pythonburp/ui/PackageManagerController.java`: EDT-safe UI orchestration.

Modified production files:

- `src/main/java/com/pythonburp/cache/CacheManager.java`: expose the extension data root consistently.
- `src/main/java/com/pythonburp/python/CPythonRuntimeFactory.java`: use `ExtensionDataPaths` and pass user package path to workers.
- `src/main/java/com/pythonburp/python/CPythonWorkerRuntime.java`: prepend user package directory through `PYTHONPATH` and clean per-run RPC directories.
- `src/main/java/com/pythonburp/python/ScriptExecutor.java`: acquire and release script activity leases.
- `src/main/java/com/pythonburp/concurrency/IdeExecutors.java`: retain one serialized package executor and expose shutdown state where needed.
- `src/main/java/com/pythonburp/core/ExtensionContext.java`: own the coordinator, package service, and cleaner lifecycle.
- `src/main/java/com/pythonburp/BurpPythonIdeExtension.java`: startup tombstone cleanup and dependency wiring.
- `src/main/java/com/pythonburp/ui/BurpPythonIdeTab.java`: top-level `Editor` and `Package Manager` workspace tabs.
- `README.md`: package manager usage, risk warning, storage path, and cleanup instructions.

New tests mirror each production unit under `src/test/java/com/pythonburp/...`.

## Task 0: Checkpoint the Existing Working Baseline

The worktree already contains completed editor actions, Diagnostic UI removal, `pypdf`, runtime cache invalidation, and timeout cleanup. Preserve those changes as a separate baseline commit before Package Manager work.

**Files:**
- Existing modified files shown by `git status --short`

- [ ] **Step 1: Run the existing full verification suite**

Run:

```powershell
./gradlew.bat build --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`, including `test`, `packageSmokeTest`, and `fatJarIsolatedSmoke`.

- [ ] **Step 2: Review the baseline diff and exclude local-only directories**

Run:

```powershell
git status --short
git diff --check
```

Expected: `.vscode/`, `bin/`, and `.superpowers/` remain untracked and are not staged.

- [ ] **Step 3: Commit only the completed baseline feature files**

```powershell
git add README.md build.gradle scripts/prepare-cpython-bundle.ps1 `
  src/main/java/com/pythonburp/BurpPythonIdeExtension.java `
  src/main/java/com/pythonburp/python/CPythonRuntimeFactory.java `
  src/main/java/com/pythonburp/python/CPythonWorkerRuntime.java `
  src/main/java/com/pythonburp/ui/BurpPythonIdeTab.java `
  src/main/java/com/pythonburp/ui/ConsolePanel.java `
  src/main/java/com/pythonburp/ui/ScriptFileService.java `
  src/main/resources/package-catalog.json `
  src/test/java/com/pythonburp/catalog/PackageCatalogLoaderTest.java `
  src/test/java/com/pythonburp/ui/BurpPythonIdeTabTest.java `
  src/test/java/com/pythonburp/ui/ConsolePanelTest.java `
  src/test/java/com/pythonburp/ui/ScriptFileServiceTest.java
git add -u src/main/java/com/pythonburp/ui/PackageCatalogPanel.java src/test/java/com/pythonburp/ui/PackageCatalogPanelTest.java
git commit -m "feat: streamline IDE and bundle pypdf"
```

Expected: Package Manager work starts from a reproducible committed baseline.

## Task 1: Establish One Confined Extension Data Root

**Files:**
- Create: `src/main/java/com/pythonburp/storage/ExtensionDataPaths.java`
- Test: `src/test/java/com/pythonburp/storage/ExtensionDataPathsTest.java`
- Modify: `src/main/java/com/pythonburp/cache/CacheManager.java`

- [ ] **Step 1: Write failing path-layout and confinement tests**

```java
@Test
void exposesAllOwnedPathsBelowOneRoot() {
    ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("BurpPythonIDE"));

    assertEquals(paths.root().resolve("runtime"), paths.runtimeRoot());
    assertEquals(paths.root().resolve("packages/cpython-3.12-windows-x64"), paths.userPackages());
    assertEquals(paths.root().resolve("pip-cache"), paths.pipCache());
    assertEquals(paths.root().resolve("settings"), paths.settings());
    assertTrue(paths.isOwned(paths.userPackages()));
}

@Test
void rejectsPathsOutsideOwnedRoot() {
    ExtensionDataPaths paths = new ExtensionDataPaths(tempDir.resolve("BurpPythonIDE"));
    assertThrows(IOException.class, () -> paths.requireOwned(tempDir.resolve("outside")));
}
```

- [ ] **Step 2: Run the tests and verify they fail**

```powershell
./gradlew.bat test --tests com.pythonburp.storage.ExtensionDataPathsTest --no-daemon --console=plain
```

Expected: compilation fails because `ExtensionDataPaths` does not exist.

- [ ] **Step 3: Implement the path model**

```java
public final class ExtensionDataPaths {
    private final Path root;

    public ExtensionDataPaths(Path root) {
        this.root = Objects.requireNonNull(root).toAbsolutePath().normalize();
    }

    public static ExtensionDataPaths windowsDefault() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = local == null || local.isBlank()
            ? Path.of(System.getProperty("user.home"), "AppData", "Local")
            : Path.of(local);
        return new ExtensionDataPaths(base.resolve("BurpPythonIDE"));
    }

    public Path root() { return root; }
    public Path runtimeRoot() { return root.resolve("runtime"); }
    public Path userPackages() { return root.resolve("packages/cpython-3.12-windows-x64"); }
    public Path packageStagingRoot() { return root.resolve("packages/staging"); }
    public Path packageRequests() { return root.resolve("packages/requests.properties"); }
    public Path packageSources() { return root.resolve("packages/sources"); }
    public Path pipCache() { return root.resolve("pip-cache"); }
    public Path temp() { return root.resolve("temp"); }
    public Path logs() { return root.resolve("logs"); }
    public Path settings() { return root.resolve("settings"); }

    public Path requireOwned(Path candidate) throws IOException {
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new IOException("Refusing path outside extension data root: " + candidate);
        }
        return normalized;
    }
}
```

Change `CacheManager.defaultWindowsRoot()` to delegate to `ExtensionDataPaths.windowsDefault().runtimeRoot()` so old callers no longer create a second root layout.

- [ ] **Step 4: Run path and cache tests**

```powershell
./gradlew.bat test --tests com.pythonburp.storage.ExtensionDataPathsTest --tests com.pythonburp.cache.CacheManagerTest --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/pythonburp/storage/ExtensionDataPaths.java src/test/java/com/pythonburp/storage/ExtensionDataPathsTest.java src/main/java/com/pythonburp/cache/CacheManager.java
git commit -m "feat: define extension data root"
```

## Task 2: Coordinate Script Runs and Package Mutations

**Files:**
- Create: `src/main/java/com/pythonburp/concurrency/RuntimeActivityCoordinator.java`
- Test: `src/test/java/com/pythonburp/concurrency/RuntimeActivityCoordinatorTest.java`
- Modify: `src/main/java/com/pythonburp/python/ScriptExecutor.java`
- Modify: `src/test/java/com/pythonburp/python/ScriptExecutorTest.java`

- [ ] **Step 1: Write failing mutual-exclusion tests**

```java
@Test
void packageMutationIsRejectedWhileScriptLeaseIsActive() throws Exception {
    RuntimeActivityCoordinator coordinator = new RuntimeActivityCoordinator();
    try (RuntimeActivityCoordinator.Lease ignored = coordinator.beginScript()) {
        assertThrows(IllegalStateException.class, coordinator::beginPackageMutation);
    }
}

@Test
void scriptIsRejectedWhilePackageMutationIsActive() throws Exception {
    RuntimeActivityCoordinator coordinator = new RuntimeActivityCoordinator();
    try (RuntimeActivityCoordinator.Lease ignored = coordinator.beginPackageMutation()) {
        assertThrows(IllegalStateException.class, coordinator::beginScript);
    }
}

@Test
void multipleScriptsCanRunWhenNoMutationIsActive() throws Exception {
    RuntimeActivityCoordinator coordinator = new RuntimeActivityCoordinator();
    try (var first = coordinator.beginScript(); var second = coordinator.beginScript()) {
        assertEquals(2, coordinator.snapshot().activeScripts());
    }
}
```

- [ ] **Step 2: Run the coordinator tests and verify failure**

```powershell
./gradlew.bat test --tests com.pythonburp.concurrency.RuntimeActivityCoordinatorTest --no-daemon --console=plain
```

Expected: compilation failure for the missing coordinator.

- [ ] **Step 3: Implement leases with synchronized state**

```java
public final class RuntimeActivityCoordinator {
    private int activeScripts;
    private boolean packageMutation;

    public synchronized Lease beginScript() {
        if (packageMutation) throw new IllegalStateException("Package operation is active");
        activeScripts++;
        return new Lease(() -> endScript());
    }

    public synchronized Lease beginPackageMutation() {
        if (packageMutation || activeScripts > 0) {
            throw new IllegalStateException("Python runtime is busy");
        }
        packageMutation = true;
        return new Lease(() -> endPackageMutation());
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(activeScripts, packageMutation);
    }

    public record Snapshot(int activeScripts, boolean packageMutation) {}

    public static final class Lease implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final Runnable release;
        private Lease(Runnable release) { this.release = release; }
        @Override public void close() {
            if (closed.compareAndSet(false, true)) release.run();
        }
    }
}
```

- [ ] **Step 4: Make `ScriptExecutor` hold a script lease for the full worker lifetime**

Update its constructor and task body:

```java
public ScriptExecutor(IdeExecutors executors, Supplier<PythonRuntime> runtimeFactory,
                      RuntimeActivityCoordinator coordinator) {
    this.executors = Objects.requireNonNull(executors);
    this.runtimeFactory = Objects.requireNonNull(runtimeFactory);
    this.coordinator = Objects.requireNonNull(coordinator);
}

return executors.submitScript(() -> {
    try (var lease = coordinator.beginScript();
         PythonRuntime runtime = runtimeFactory.get()) {
        return runtime.execute(request.source(), request.timeout());
    }
});
```

- [ ] **Step 5: Run coordinator and script executor tests**

```powershell
./gradlew.bat test --tests com.pythonburp.concurrency.RuntimeActivityCoordinatorTest --tests com.pythonburp.python.ScriptExecutorTest --no-daemon --console=plain
```

Expected: PASS, including lease release after success and exception.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/pythonburp/concurrency/RuntimeActivityCoordinator.java src/test/java/com/pythonburp/concurrency/RuntimeActivityCoordinatorTest.java src/main/java/com/pythonburp/python/ScriptExecutor.java src/test/java/com/pythonburp/python/ScriptExecutorTest.java
git commit -m "feat: coordinate scripts and package operations"
```

## Task 3: Prepend Persistent User Packages to Every Worker

**Files:**
- Modify: `src/main/java/com/pythonburp/python/CPythonWorkerRuntime.java`
- Modify: `src/main/java/com/pythonburp/python/CPythonRuntimeFactory.java`
- Modify: `src/test/java/com/pythonburp/python/CPythonWorkerRuntimeTest.java`
- Create: `src/test/java/com/pythonburp/python/CPythonUserPackagePrecedenceTest.java`

- [ ] **Step 1: Write a failing worker environment test**

Create a fake interpreter script that prints `BURP_PYTHON_USER_PACKAGES`, then assert the configured directory is present:

```java
CPythonWorkerRuntime runtime = new CPythonWorkerRuntime(
    fakeCommand,
    tempDir.resolve("work"),
    new BurpBridge(),
    tempDir.resolve("user-packages")
);
ScriptRunResult result = runtime.execute("print('ignored')", Duration.ofSeconds(5));
assertTrue(result.stdout().contains(tempDir.resolve("user-packages").toString()));
```

- [ ] **Step 2: Run the test and verify failure**

```powershell
./gradlew.bat test --tests com.pythonburp.python.CPythonWorkerRuntimeTest --no-daemon --console=plain
```

Expected: constructor mismatch or missing environment variable.

- [ ] **Step 3: Pass the package directory through the worker process environment**

```java
builder.environment().put("BURP_PYTHON_RPC_DIR", rpcDirectory.toString());
builder.environment().put("BURP_PYTHON_USER_PACKAGES", userPackages.toString());
String existing = builder.environment().getOrDefault("PYTHONPATH", "");
builder.environment().put(
    "PYTHONPATH",
    existing.isBlank() ? userPackages.toString() : userPackages + File.pathSeparator + existing
);
```

Create the user package directory before process start. Update `CPythonRuntimeFactory` to accept `ExtensionDataPaths` and construct workers with `paths.userPackages()`.

- [ ] **Step 4: Write and run a real precedence integration test**

Place a temporary module named `precedence_probe.py` in the user package directory with `VALUE = 'user'`, place a bundled-path copy with `VALUE = 'bundle'`, run:

```python
import precedence_probe
print(precedence_probe.VALUE)
```

Expected stdout: `user`.

Run:

```powershell
./gradlew.bat test --tests com.pythonburp.python.CPythonUserPackagePrecedenceTest --tests com.pythonburp.python.CPythonWorkerRuntimeTest --no-daemon --console=plain
```

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/pythonburp/python/CPythonWorkerRuntime.java src/main/java/com/pythonburp/python/CPythonRuntimeFactory.java src/test/java/com/pythonburp/python/CPythonWorkerRuntimeTest.java src/test/java/com/pythonburp/python/CPythonUserPackagePrecedenceTest.java
git commit -m "feat: load persistent user packages first"
```

## Task 4: Persist and Sanitize pip Network Settings

**Files:**
- Create: `src/main/java/com/pythonburp/packages/PackageManagerSettings.java`
- Create: `src/main/java/com/pythonburp/packages/PackageSettingsStore.java`
- Test: `src/test/java/com/pythonburp/packages/PackageSettingsStoreTest.java`
- Test: `src/test/java/com/pythonburp/packages/PackageManagerSettingsTest.java`

- [ ] **Step 1: Write failing settings tests**

```java
@Test
void roundTripsPackageSettings() throws Exception {
    PackageSettingsStore store = new PackageSettingsStore(tempDir.resolve("settings/pip.properties"));
    PackageManagerSettings expected = new PackageManagerSettings(
        "https://pypi.org/simple", "", "http://user:secret@proxy:8080", List.of("internal.example"), 60
    );
    store.save(expected);
    assertEquals(expected, store.load());
}

@Test
void redactsProxyCredentials() {
    PackageManagerSettings settings = new PackageManagerSettings("", "", "http://user:secret@proxy:8080", List.of(), 30);
    assertFalse(settings.sanitizedSummary().contains("secret"));
    assertTrue(settings.sanitizedSummary().contains("proxy:8080"));
}
```

- [ ] **Step 2: Run tests and verify failure**

```powershell
./gradlew.bat test --tests com.pythonburp.packages.PackageSettingsStoreTest --tests com.pythonburp.packages.PackageManagerSettingsTest --no-daemon --console=plain
```

- [ ] **Step 3: Implement immutable settings and an atomic properties store**

Use a record with normalized blanks and timeout validation:

```java
public record PackageManagerSettings(
    String indexUrl,
    String extraIndexUrl,
    String proxyUrl,
    List<String> trustedHosts,
    int timeoutSeconds
) {
    public PackageManagerSettings {
        indexUrl = normalize(indexUrl);
        extraIndexUrl = normalize(extraIndexUrl);
        proxyUrl = normalize(proxyUrl);
        trustedHosts = List.copyOf(trustedHosts == null ? List.of() : trustedHosts);
        if (timeoutSeconds < 1 || timeoutSeconds > 600) throw new IllegalArgumentException("timeoutSeconds");
    }
}
```

Write `PackageSettingsStore.save()` to a sibling temporary file and replace the destination atomically where supported.

- [ ] **Step 4: Run settings tests**

```powershell
./gradlew.bat test --tests com.pythonburp.packages.PackageSettingsStoreTest --tests com.pythonburp.packages.PackageManagerSettingsTest --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/pythonburp/packages/PackageManagerSettings.java src/main/java/com/pythonburp/packages/PackageSettingsStore.java src/test/java/com/pythonburp/packages/PackageManagerSettingsTest.java src/test/java/com/pythonburp/packages/PackageSettingsStoreTest.java
git commit -m "feat: persist pip network settings"
```

## Task 5: Build Safe pip Commands and Stream Process Output

**Files:**
- Create: `src/main/java/com/pythonburp/packages/PipCommandFactory.java`
- Create: `src/main/java/com/pythonburp/packages/PipRunResult.java`
- Create: `src/main/java/com/pythonburp/packages/EmbeddedPipRunner.java`
- Test: `src/test/java/com/pythonburp/packages/PipCommandFactoryTest.java`
- Test: `src/test/java/com/pythonburp/packages/EmbeddedPipRunnerTest.java`

- [ ] **Step 1: Write failing argument-construction tests**

```java
@Test
void requirementIsOneProcessArgument() {
    List<String> command = factory.installRequirement("requests>=2.34,<3", target, settings);
    assertTrue(command.contains("requests>=2.34,<3"));
    assertFalse(command.contains("cmd.exe"));
    assertFalse(command.contains("powershell.exe"));
}

@Test
void wheelAndRequirementsPathsArePassedWithoutShellQuoting() {
    assertTrue(factory.installWheel(Path.of("C:/With Space/demo.whl"), target, settings)
        .contains("C:\\With Space\\demo.whl"));
    assertTrue(factory.installRequirements(Path.of("C:/With Space/requirements.txt"), target, settings)
        .contains("C:\\With Space\\requirements.txt"));
}
```

- [ ] **Step 2: Implement `PipCommandFactory`**

Base command:

```java
List<String> command = new ArrayList<>(List.of(
    python.toString(), "-m", "pip", "install", "--upgrade",
    "--target", target.toString(),
    "--cache-dir", paths.pipCache().toString(),
    "--disable-pip-version-check",
    "--no-input",
    "--timeout", Integer.toString(settings.timeoutSeconds())
));
```

Append index, extra-index, proxy, and each trusted host as separate arguments. Never log the unsanitized proxy argument.

- [ ] **Step 3: Write failing runner tests using fake PowerShell processes**

```java
@Test
void streamsStdoutAndStderrAndReturnsExitCode() throws Exception {
    List<String> lines = new CopyOnWriteArrayList<>();
    PipRunResult result = runner.run(fakeCommand("Write-Output out; Write-Error err; exit 7"), lines::add, token);
    assertEquals(7, result.exitCode());
    assertTrue(lines.stream().anyMatch(line -> line.contains("out")));
    assertTrue(lines.stream().anyMatch(line -> line.contains("err")));
}

@Test
void cancellationTerminatesProcessTree() throws Exception {
    CancellationToken token = new CancellationToken();
    Future<PipRunResult> run = executor.submit(() -> runner.run(longCommand, ignored -> {}, token));
    token.cancel();
    assertTrue(run.get(15, SECONDS).cancelled());
}
```

- [ ] **Step 4: Implement `EmbeddedPipRunner`**

Use `ProcessBuilder(command)` directly, separate stdout/stderr reader threads, `ConsoleBuffer` with a fixed capacity, and the same process-tree termination pattern as `CPythonWorkerRuntime`. Return:

```java
public record PipRunResult(int exitCode, boolean cancelled, String stdout, String stderr) {
    public boolean succeeded() { return !cancelled && exitCode == 0; }
}
```

- [ ] **Step 5: Run command and runner tests**

```powershell
./gradlew.bat test --tests com.pythonburp.packages.PipCommandFactoryTest --tests com.pythonburp.packages.EmbeddedPipRunnerTest --no-daemon --console=plain
```

Expected: PASS with no leaked fake processes.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/pythonburp/packages/PipCommandFactory.java src/main/java/com/pythonburp/packages/PipRunResult.java src/main/java/com/pythonburp/packages/EmbeddedPipRunner.java src/test/java/com/pythonburp/packages/PipCommandFactoryTest.java src/test/java/com/pythonburp/packages/EmbeddedPipRunnerTest.java
git commit -m "feat: run embedded pip safely"
```

## Task 6: Implement Transactional Requests, Inventory, and Environment Swaps

**Files:**
- Create: `src/main/java/com/pythonburp/packages/PackageRequest.java`
- Create: `src/main/java/com/pythonburp/packages/PackageRequestStore.java`
- Create: `src/main/java/com/pythonburp/packages/PackageInventoryEntry.java`
- Create: `src/main/java/com/pythonburp/packages/PackageInventoryReader.java`
- Create: `src/main/java/com/pythonburp/packages/SharedPackageEnvironment.java`
- Test: `src/test/java/com/pythonburp/packages/PackageRequestStoreTest.java`
- Test: `src/test/java/com/pythonburp/packages/PackageInventoryReaderTest.java`
- Test: `src/test/java/com/pythonburp/packages/SharedPackageEnvironmentTest.java`

- [ ] **Step 1: Write failing package request persistence tests**

Model only top-level user intent:

```java
sealed interface PackageRequest permits PyPiRequest, WheelRequest, RequirementsRequest {
    String id();
}
record PyPiRequest(String id, String requirement) implements PackageRequest {}
record WheelRequest(String id, Path managedWheel) implements PackageRequest {}
record RequirementsRequest(String id, Path managedRequirements) implements PackageRequest {}
```

Test round-trip ordering and replacement by normalized id.

- [ ] **Step 2: Write failing transactional swap tests**

```java
@Test
void failedStagingBuildLeavesActiveEnvironmentUntouched() throws Exception {
    Files.createDirectories(paths.userPackages());
    Files.writeString(paths.userPackages().resolve("working.txt"), "old");

    assertThrows(IOException.class, () -> environment.replaceWith(staging -> {
        Files.writeString(staging.resolve("partial.txt"), "new");
        throw new IOException("pip failed");
    }));

    assertEquals("old", Files.readString(paths.userPackages().resolve("working.txt")));
}

@Test
void successfulBuildAtomicallyReplacesActiveEnvironment() throws Exception {
    environment.replaceWith(staging -> Files.writeString(staging.resolve("new.txt"), "new"));
    assertTrue(Files.exists(paths.userPackages().resolve("new.txt")));
}
```

- [ ] **Step 3: Implement staging and rollback**

`SharedPackageEnvironment.replaceWith()` must:

1. Create a unique directory under `packages/staging`.
2. Invoke the builder with that directory.
3. Rename active packages to `packages/delete-pending-<timestamp>`.
4. Move staging to the active path.
5. Delete the old environment in the background or leave it for startup cleanup.
6. Restore the old environment if the active swap fails.

All move/delete targets must pass `ExtensionDataPaths.requireOwned()`.

- [ ] **Step 4: Implement inventory through an embedded Python helper**

Run this source through the embedded interpreter with the target directory prepended:

```python
import importlib.metadata as md
import json
import pathlib

rows = []
for dist in md.distributions():
    root = pathlib.Path(dist.locate_file(""))
    files = [str(item) for item in (dist.files or [])]
    rows.append({
        "name": dist.metadata.get("Name", ""),
        "version": dist.version,
        "native": any(item.lower().endswith((".pyd", ".dll")) for item in files),
        "root": str(root),
    })
print(json.dumps(rows))
```

Parse the JSON with a small focused parser consistent with `PackageCatalogLoader`, then merge with bundled catalog entries by normalized distribution name.

- [ ] **Step 5: Run request, inventory, and environment tests**

```powershell
./gradlew.bat test --tests com.pythonburp.packages.PackageRequestStoreTest --tests com.pythonburp.packages.PackageInventoryReaderTest --tests com.pythonburp.packages.SharedPackageEnvironmentTest --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/pythonburp/packages/PackageRequest.java src/main/java/com/pythonburp/packages/PackageRequestStore.java src/main/java/com/pythonburp/packages/PackageInventoryEntry.java src/main/java/com/pythonburp/packages/PackageInventoryReader.java src/main/java/com/pythonburp/packages/SharedPackageEnvironment.java src/test/java/com/pythonburp/packages/PackageRequestStoreTest.java src/test/java/com/pythonburp/packages/PackageInventoryReaderTest.java src/test/java/com/pythonburp/packages/SharedPackageEnvironmentTest.java
git commit -m "feat: add transactional package environment"
```

## Task 7: Implement Package Manager Workflows

**Files:**
- Create: `src/main/java/com/pythonburp/packages/PackageManagerService.java`
- Test: `src/test/java/com/pythonburp/packages/PackageManagerServiceTest.java`
- Add fixtures: `src/test/resources/packages/demo-pure-python.whl`
- Add fixtures: `src/test/resources/packages/requirements.txt`

- [ ] **Step 1: Write failing install and fallback tests**

Use a fake `EmbeddedPipRunner` that records commands and writes a synthetic `.dist-info` tree into staging:

```java
@Test
void installRequirementRebuildsEnvironmentAndPersistsRequest() throws Exception {
    PackageOperationResult result = service.installRequirement("demo-package==1.2.3", output);
    assertTrue(result.succeeded());
    assertEquals("demo-package==1.2.3", requestStore.load().get(0).displayValue());
    assertTrue(Files.exists(paths.userPackages().resolve("demo_package/__init__.py")));
}

@Test
void uninstallRemovesUserRequestAndRestoresBundledFallback() throws Exception {
    service.installRequirement("requests==9.9.9", output);
    service.uninstall("requests", output);
    PackageInventoryEntry requests = service.inventory().stream()
        .filter(entry -> entry.name().equalsIgnoreCase("requests"))
        .findFirst().orElseThrow();
    assertEquals("Bundled", requests.source());
}
```

- [ ] **Step 2: Implement transactional rebuild for every mutation**

`PackageManagerService` workflow:

```java
try (var lease = coordinator.beginPackageMutation()) {
    List<PackageRequest> next = mutate(requestStore.load());
    environment.replaceWith(staging -> {
        for (PackageRequest request : next) {
            PipRunResult result = pipRunner.run(
                commandFactory.install(request, staging, settingsStore.load()), output, cancellationToken
            );
            if (!result.succeeded()) throw new PackageOperationException(result);
        }
        inventoryReader.read(staging);
    });
    requestStore.save(next);
    return PackageOperationResult.success(inventory());
}
```

For a local wheel or requirements file, copy the selected input into `paths.packageSources()` before recording the managed path. Reject directories and missing files.

- [ ] **Step 3: Add install, upgrade, uninstall, and repair semantics**

- Install replaces an existing request with the same normalized package id.
- Upgrade reuses the request without a pinned version or accepts a new requirement string.
- Uninstall removes only the matching top-level user request and rebuilds dependencies from remaining requests.
- Repair rebuilds the active environment from the unchanged request store.
- Failed rebuild leaves both active environment and request store unchanged.

- [ ] **Step 4: Run service tests**

```powershell
./gradlew.bat test --tests com.pythonburp.packages.PackageManagerServiceTest --no-daemon --console=plain
```

Expected: PASS for PyPI, wheel, requirements, rollback, uninstall fallback, and coordinator rejection.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/pythonburp/packages/PackageManagerService.java src/test/java/com/pythonburp/packages/PackageManagerServiceTest.java src/test/resources/packages
git commit -m "feat: add hybrid package workflows"
```

## Task 8: Add Controlled Cleanup and Full Reset

**Files:**
- Create: `src/main/java/com/pythonburp/storage/ExtensionDataCleaner.java`
- Test: `src/test/java/com/pythonburp/storage/ExtensionDataCleanerTest.java`
- Modify: `src/main/java/com/pythonburp/packages/PackageManagerService.java`
- Modify: `src/test/java/com/pythonburp/packages/PackageManagerServiceTest.java`

- [ ] **Step 1: Write failing confinement and cleanup tests**

```java
@Test
void clearPackagesDoesNotDeleteRuntimeSettingsOrExternalFiles() throws Exception {
    Path external = tempDir.resolve("saved-script.py");
    Files.writeString(external, "print('keep')");
    createOwnedData(paths);

    cleaner.clearUserPackages();

    assertTrue(Files.exists(paths.runtimeRoot()));
    assertTrue(Files.exists(paths.settings()));
    assertTrue(Files.exists(external));
    assertTrue(Files.isDirectory(paths.userPackages()));
}

@Test
void resetRejectsADataRootOutsideLocalAppDataParent() {
    ExtensionDataPaths unsafe = new ExtensionDataPaths(Path.of("C:/"));
    assertThrows(IOException.class, () -> new ExtensionDataCleaner(unsafe).resetAll());
}
```

- [ ] **Step 2: Implement tombstone deletion**

Use sibling names:

```java
Path tombstone = root.resolveSibling("BurpPythonIDE.delete-pending-" + clock.millis());
Files.move(root, tombstone, StandardCopyOption.ATOMIC_MOVE);
Files.createDirectories(root);
deleteRecursivelyConfined(tombstone, root.getParent());
```

Before recursive deletion, verify the normalized target starts with the expected Local AppData parent and its filename starts with `BurpPythonIDE.delete-pending-`.

- [ ] **Step 3: Add startup stale-tombstone cleanup**

`cleanupPending()` scans only siblings matching the exact prefix and retries best-effort deletion. It returns remaining locked paths for display/logging.

- [ ] **Step 4: Wire cleanup through package mutation leases**

`PackageManagerService.clearUserPackages()`, `clearPipCache()`, and `resetAllExtensionData()` must acquire `beginPackageMutation()`. Full reset marks the service unusable until extension reload.

- [ ] **Step 5: Run cleanup and service tests**

```powershell
./gradlew.bat test --tests com.pythonburp.storage.ExtensionDataCleanerTest --tests com.pythonburp.packages.PackageManagerServiceTest --no-daemon --console=plain
```

Expected: PASS, including external-file preservation and locked tombstone reporting.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/pythonburp/storage/ExtensionDataCleaner.java src/test/java/com/pythonburp/storage/ExtensionDataCleanerTest.java src/main/java/com/pythonburp/packages/PackageManagerService.java src/test/java/com/pythonburp/packages/PackageManagerServiceTest.java
git commit -m "feat: add controlled extension cleanup"
```

## Task 9: Build the Package Manager Workspace UI

**Files:**
- Create: `src/main/java/com/pythonburp/ui/PackageManagerPanel.java`
- Create: `src/main/java/com/pythonburp/ui/PackageManagerController.java`
- Test: `src/test/java/com/pythonburp/ui/PackageManagerPanelTest.java`
- Test: `src/test/java/com/pythonburp/ui/PackageManagerControllerTest.java`
- Modify: `src/main/java/com/pythonburp/ui/BurpPythonIdeTab.java`
- Modify: `src/test/java/com/pythonburp/ui/BurpPythonIdeTabTest.java`

- [ ] **Step 1: Write failing workspace layout tests**

```java
@Test
void ideContainsEditorAndPackageManagerWorkspaceTabs() throws Exception {
    AtomicReference<List<String>> titles = new AtomicReference<>();
    onEdt(() -> titles.set(findTabbedPane(newTab()).titles()));
    assertEquals(List.of("Editor", "Package Manager"), titles.get());
}

@Test
void packagePanelContainsApprovedActions() throws Exception {
    onEdt(() -> {
        PackageManagerPanel panel = new PackageManagerPanel();
        assertTrue(buttonLabels(panel).containsAll(List.of(
            "Install", "Install Wheel", "Install Requirements", "Refresh", "Settings",
            "Clear User Packages", "Clear pip Cache", "Reset All Extension Data"
        )));
    });
}
```

- [ ] **Step 2: Implement the static Swing panel**

Use:

- `JTextField` for PyPI requirement.
- `JButton` commands.
- `JTable` with columns Name, Active Version, Source, Bundled Fallback, Native, Actions.
- Read-only `JTextArea` for bounded pip output.
- `JLabel` for storage usage and activity status.
- A non-modal settings dialog using text fields and a timeout spinner.

Keep page sections unframed; use a single `JTabbedPane` at the workspace level and avoid nested decorative cards.

- [ ] **Step 3: Write failing controller state tests**

```java
@Test
void mutationControlsDisableWhileOperationRuns() throws Exception {
    controller.installRequirement("demo");
    assertFalse(panel.installButton().isEnabled());
    operation.complete(successResult);
    flushEdt();
    assertTrue(panel.installButton().isEnabled());
}

@Test
void pipOutputIsPublishedOnEdtInBatches() throws Exception {
    controller.acceptOutput("Collecting demo");
    controller.flushOutput();
    flushEdt();
    assertTrue(panel.outputText().contains("Collecting demo"));
}
```

- [ ] **Step 4: Implement `PackageManagerController`**

The controller must:

- Submit mutations through `IdeExecutors.submitPackageTask`.
- Never call `Future.get()` on EDT.
- Use `ConsoleBuffer` and a Swing `Timer` to flush pip output every 100 ms.
- Update table/status through `Edt.runLater`.
- Open file choosers on EDT, but perform copies and pip work in background.
- Require a confirmation dialog containing the exact Local AppData root before full reset.
- Show the third-party-code warning beside install controls.

- [ ] **Step 5: Integrate workspace tabs into `BurpPythonIdeTab`**

```java
JTabbedPane workspaces = new JTabbedPane();
workspaces.addTab("Editor", editorWorkspace);
workspaces.addTab("Package Manager", packageManagerPanel);
add(workspaces, BorderLayout.CENTER);
```

Keep the editor toolbar and console inside the Editor tab so Package Manager has its own pip output.

- [ ] **Step 6: Run UI tests**

```powershell
./gradlew.bat test --tests com.pythonburp.ui.PackageManagerPanelTest --tests com.pythonburp.ui.PackageManagerControllerTest --tests com.pythonburp.ui.BurpPythonIdeTabTest --no-daemon --console=plain
```

Expected: PASS and no Swing work off EDT assertions.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/pythonburp/ui/PackageManagerPanel.java src/main/java/com/pythonburp/ui/PackageManagerController.java src/main/java/com/pythonburp/ui/BurpPythonIdeTab.java src/test/java/com/pythonburp/ui/PackageManagerPanelTest.java src/test/java/com/pythonburp/ui/PackageManagerControllerTest.java src/test/java/com/pythonburp/ui/BurpPythonIdeTabTest.java
git commit -m "feat: add package manager workspace"
```

## Task 10: Wire Lifecycle, Startup Cleanup, and Dependency Ownership

**Files:**
- Modify: `src/main/java/com/pythonburp/core/ExtensionContext.java`
- Modify: `src/main/java/com/pythonburp/BurpPythonIdeExtension.java`
- Modify: `src/test/java/com/pythonburp/BurpPythonIdeExtensionTest.java`
- Modify: `src/main/java/com/pythonburp/ui/BurpPythonIdeTab.java`

- [ ] **Step 1: Write failing extension lifecycle tests**

```java
@Test
void initializeSchedulesPendingCleanupOffEdt() {
    extension.initialize(stub.api());
    assertTrue(stub.pendingCleanupWasScheduled);
    assertFalse(stub.pendingCleanupRanOnEdt);
}

@Test
void unloadCancelsPackageWorkAndClosesExecutors() {
    extension.initialize(stub.api());
    stub.unloadingHandlers.get(0).extensionUnloaded();
    assertThrows(RejectedExecutionException.class, () -> context.executors().submitPackageTask(() -> 1));
}
```

- [ ] **Step 2: Expand `ExtensionContext` ownership**

Construct and expose:

```java
ExtensionDataPaths paths;
RuntimeActivityCoordinator coordinator;
IdeExecutors executors;
CPythonRuntimeFactory runtimeFactory;
PackageManagerService packageManagerService;
ExtensionDataCleaner cleaner;
```

`close()` cancels active package operations before shutting down executors.

- [ ] **Step 3: Wire startup without blocking Burp EDT**

`initialize()` must:

1. Create paths/coordinator/services synchronously without filesystem traversal.
2. Register the UI on EDT.
3. Submit `cleaner.cleanupPending()` and initial inventory refresh to the package executor.
4. Publish remaining locked tombstones to Burp logging and Package Manager status.

- [ ] **Step 4: Run lifecycle tests**

```powershell
./gradlew.bat test --tests com.pythonburp.BurpPythonIdeExtensionTest --tests com.pythonburp.core.ExtensionContextTest --no-daemon --console=plain
```

Expected: PASS, with UI registration still on EDT and cleanup off EDT.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/pythonburp/core/ExtensionContext.java src/main/java/com/pythonburp/BurpPythonIdeExtension.java src/main/java/com/pythonburp/ui/BurpPythonIdeTab.java src/test/java/com/pythonburp/BurpPythonIdeExtensionTest.java src/test/java/com/pythonburp/core/ExtensionContextTest.java
git commit -m "feat: wire package manager lifecycle"
```

## Task 11: Add Real Package Integration Coverage

**Files:**
- Create: `src/test/java/com/pythonburp/packages/PackageManagerIntegrationTest.java`
- Create: `src/test/resources/packages/build-demo-wheel.ps1`
- Modify: `build.gradle`

- [ ] **Step 1: Add a deterministic local wheel fixture task**

Create a tiny pure-Python wheel fixture without network dependency. The PowerShell script writes a ZIP-format wheel containing:

```text
demo_package/__init__.py
demo_package-1.0.0.dist-info/METADATA
demo_package-1.0.0.dist-info/WHEEL
demo_package-1.0.0.dist-info/RECORD
```

`__init__.py` contains:

```python
VALUE = "installed-from-wheel"
```

Add a Gradle task `preparePackageFixtures` and make the integration test depend on it.

- [ ] **Step 2: Write integration tests against embedded pip**

```java
@Test
void installsLocalWheelAndImportsItFromNewWorker() throws Exception {
    service.installWheel(wheel, ignored -> {});
    try (PythonRuntime runtime = runtimeFactory.get()) {
        ScriptRunResult result = runtime.execute(
            "import demo_package; print(demo_package.VALUE)", Duration.ofSeconds(30));
        assertEquals(ScriptStatus.SUCCEEDED, result.status(), result.stderr());
        assertTrue(result.stdout().contains("installed-from-wheel"));
    }
}

@Test
void clearPackagesRemovesWheelAndLeavesExternalScript() throws Exception {
    service.installWheel(wheel, ignored -> {});
    service.clearUserPackages();
    assertFalse(Files.exists(paths.userPackages().resolve("demo_package")));
    assertTrue(Files.exists(externalScript));
}
```

- [ ] **Step 3: Add a dedicated Gradle integration task**

```groovy
tasks.register("packageManagerIntegrationTest", Test) {
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.output.classesDirs
    classpath = sourceSets.test.runtimeClasspath
    include "**/PackageManagerIntegrationTest.class"
    dependsOn tasks.named("preparePackageFixtures")
    shouldRunAfter test
}
```

Exclude this class from the normal `test` task and include it in `check`.

- [ ] **Step 4: Run integration tests**

```powershell
./gradlew.bat packageManagerIntegrationTest --no-daemon --console=plain
```

Expected: PASS without PyPI access, proving wheel install, user precedence, worker import, and cleanup.

- [ ] **Step 5: Commit**

```powershell
git add src/test/java/com/pythonburp/packages/PackageManagerIntegrationTest.java src/test/resources/packages/build-demo-wheel.ps1 build.gradle
git commit -m "test: verify package manager end to end"
```

## Task 12: Documentation, Full Verification, and Burp Artifact

**Files:**
- Modify: `README.md`
- Modify: `src/main/java/com/pythonburp/core/VersionInfo.java`
- Modify: `build.gradle`

- [ ] **Step 1: Update version and documentation**

Bump the project and extension version from `0.2.1` to `0.3.0`. Document:

- Package Manager workspace.
- PyPI, wheel, and requirements installation.
- `%LOCALAPPDATA%\BurpPythonIDE` persistence.
- Third-party package execution risk.
- User package precedence.
- Clear packages, clear pip cache, and full reset behavior.
- Reload requirement after full reset.

- [ ] **Step 2: Run focused package tests**

```powershell
./gradlew.bat test --tests "com.pythonburp.packages.*" --tests "com.pythonburp.storage.*" --tests com.pythonburp.concurrency.RuntimeActivityCoordinatorTest --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 3: Run the complete clean build**

```powershell
./gradlew.bat clean build packageManagerIntegrationTest --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`, including unit tests, package catalog smoke tests, package manager integration, and isolated fat-JAR smoke.

- [ ] **Step 4: Verify the final JAR**

```powershell
Get-ChildItem build/libs/*-all.jar | Select-Object Name,Length,LastWriteTime
Get-FileHash build/libs/burp-python-ide-enhanced-0.3.0-all.jar -Algorithm SHA256
```

Expected: one Burp-loadable `0.3.0-all.jar` with a recorded SHA-256.

- [ ] **Step 5: Perform manual Burp verification**

1. Load `burp-python-ide-enhanced-0.3.0-all.jar`.
2. Confirm `Editor` and `Package Manager` tabs appear.
3. Install a small package from PyPI.
4. Run a script importing it.
5. Restart Burp and confirm the package persists.
6. Start a long script and confirm mutation controls are disabled.
7. Stop the script and uninstall the package.
8. Install a local wheel and a requirements file.
9. Clear user packages and confirm bundled packages still import.
10. Save an external `.py` file, run full reset, reload the extension, and confirm the external file remains.
11. Confirm no Burp UI freeze during install, refresh, size calculation, or cleanup.

- [ ] **Step 6: Commit final docs and version**

```powershell
git add README.md build.gradle src/main/java/com/pythonburp/core/VersionInfo.java
git commit -m "docs: document hybrid package manager"
```

## Completion Checklist

- [ ] Existing dirty baseline was committed separately before Package Manager implementation.
- [ ] Every new behavior followed red-green-refactor.
- [ ] No pip command uses a shell.
- [ ] User packages override bundled packages only in CPython workers.
- [ ] Failed package operations leave the previous active environment usable.
- [ ] Scripts and package mutations cannot overlap.
- [ ] Package changes persist across Burp and Windows restarts.
- [ ] Clear and reset operations cannot escape `%LOCALAPPDATA%\BurpPythonIDE`.
- [ ] External scripts saved outside the data root are preserved.
- [ ] Package output is bounded and rendered without blocking EDT.
- [ ] Full clean build and manual Burp smoke test pass.
