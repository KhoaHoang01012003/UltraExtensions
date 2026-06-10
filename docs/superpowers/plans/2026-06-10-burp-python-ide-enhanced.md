# Burp Python IDE Enhanced Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first useful release of a single-JAR Burp Suite Python IDE using Java, Montoya, GraalPy, full-access scripting, and responsive background execution.

**Architecture:** The extension is a Java/Montoya Burp extension with a Swing IDE, a background script executor, an embedded GraalPy runtime, an extension-managed cache for extracted resources, and Java-backed `burp.*` helper modules. The UI remains on Swing's Event Dispatch Thread while runtime startup, native extraction, imports, script execution, and heavy Burp operations run on background executors.

**Tech Stack:** Java 21, Gradle, Montoya API 2026.4, GraalPy 25.0.3, Swing inheriting Burp's host Look and Feel, RSyntaxTextArea 3.6.0, JUnit 6.1.0, Bouncy Castle 1.84.

---

## Source Spec

- `docs/superpowers/specs/2026-06-10-burp-python-ide-enhanced-design.md`

## File Structure

Create this project layout:

```text
settings.gradle
build.gradle
gradle.properties
.gitignore
README.md
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
docs/superpowers/plans/2026-06-10-burp-python-ide-enhanced.md
src/main/java/burp/BurpExtension.java
src/main/java/com/pythonburp/BurpPythonIdeExtension.java
src/main/java/com/pythonburp/core/ExtensionContext.java
src/main/java/com/pythonburp/core/VersionInfo.java
src/main/java/com/pythonburp/concurrency/Edt.java
src/main/java/com/pythonburp/concurrency/IdeExecutors.java
src/main/java/com/pythonburp/console/ConsoleEvent.java
src/main/java/com/pythonburp/console/ConsoleEventType.java
src/main/java/com/pythonburp/console/ConsoleBuffer.java
src/main/java/com/pythonburp/cache/CacheKey.java
src/main/java/com/pythonburp/cache/CacheManager.java
src/main/java/com/pythonburp/catalog/PackageCatalog.java
src/main/java/com/pythonburp/catalog/PackageCatalogEntry.java
src/main/java/com/pythonburp/catalog/PackageCatalogLoader.java
src/main/java/com/pythonburp/python/PythonRuntime.java
src/main/java/com/pythonburp/python/GraalPyPythonRuntime.java
src/main/java/com/pythonburp/python/ScriptRunRequest.java
src/main/java/com/pythonburp/python/ScriptRunResult.java
src/main/java/com/pythonburp/python/ScriptStatus.java
src/main/java/com/pythonburp/python/ScriptExecutor.java
src/main/java/com/pythonburp/bridge/BurpBridge.java
src/main/java/com/pythonburp/bridge/EncoderBridge.java
src/main/java/com/pythonburp/bridge/CryptoBridge.java
src/main/java/com/pythonburp/bridge/HttpBridge.java
src/main/java/com/pythonburp/bridge/MontoyaHttpBridge.java
src/main/java/com/pythonburp/ui/BurpPythonIdeTab.java
src/main/java/com/pythonburp/ui/EditorPanel.java
src/main/java/com/pythonburp/ui/ConsolePanel.java
src/main/java/com/pythonburp/ui/PackageCatalogPanel.java
src/main/java/com/pythonburp/ui/StatusBar.java
src/main/resources/GRAALPY-VFS/com.pythonburp/burp-python-ide/src/burp/__init__.py
src/main/resources/GRAALPY-VFS/com.pythonburp/burp-python-ide/src/burp/encoder.py
src/main/resources/GRAALPY-VFS/com.pythonburp/burp-python-ide/src/burp/crypto.py
src/main/resources/package-catalog.json
src/test/java/com/pythonburp/concurrency/IdeExecutorsTest.java
src/test/java/com/pythonburp/console/ConsoleBufferTest.java
src/test/java/com/pythonburp/cache/CacheManagerTest.java
src/test/java/com/pythonburp/catalog/PackageCatalogLoaderTest.java
src/test/java/com/pythonburp/python/GraalPyPythonRuntimeTest.java
src/test/java/com/pythonburp/python/ScriptExecutorTest.java
src/test/java/com/pythonburp/bridge/EncoderBridgeTest.java
src/test/java/com/pythonburp/bridge/CryptoBridgeTest.java
```

Responsibility boundaries:

- `burp/BurpExtension.java`: Burp's required extension entry point.
- `com.pythonburp.core`: extension lifecycle state and immutable metadata.
- `com.pythonburp.concurrency`: background executors and EDT dispatch helpers.
- `com.pythonburp.console`: bounded output buffering for print-heavy scripts.
- `com.pythonburp.cache`: deterministic cache root, version/hash keys, and resource extraction.
- `com.pythonburp.catalog`: bundled package metadata and compatibility lookup.
- `com.pythonburp.python`: GraalPy context creation and background script runs.
- `com.pythonburp.bridge`: Java objects exposed into Python.
- `com.pythonburp.ui`: Swing panels only. No runtime startup, extraction, or blocking work here.

---

### Task 1: Gradle Project Scaffold

**Files:**
- Create: `settings.gradle`
- Create: `build.gradle`
- Create: `gradle.properties`
- Create: `.gitignore`
- Create: `README.md`
- Create: `src/main/java/burp/BurpExtension.java`
- Create: `src/main/java/com/pythonburp/BurpPythonIdeExtension.java`
- Create: `src/main/java/com/pythonburp/core/VersionInfo.java`

- [ ] **Step 1: Create the Gradle settings file**

Create `settings.gradle`:

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "burp-python-ide-enhanced"
```

- [ ] **Step 2: Create Gradle project properties**

Create `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.warning.mode=all
```

- [ ] **Step 3: Create the Gradle build file**

Create `build.gradle`:

```groovy
plugins {
    id "java"
    id "org.graalvm.python" version "25.0.3"
}

group = "com.pythonburp"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

graalPy {
    packages = [
        "beautifulsoup4",
        "html5lib",
        "defusedxml",
        "xmltodict",
        "python-dateutil",
        "charset-normalizer",
        "idna",
        "tabulate",
        "rich",
        "colorama",
        "pyjwt",
        "passlib"
    ]
    resourceDirectory = "GRAALPY-VFS/com.pythonburp/burp-python-ide"
    graalPyLockFile = file("$rootDir/graalpy.lock")
}

dependencies {
    compileOnly "net.portswigger.burp.extensions:montoya-api:2026.4"

    implementation "org.graalvm.python:python-embedding:25.0.3"
    implementation "com.fifesoft:rsyntaxtextarea:3.6.0"
    implementation "org.bouncycastle:bcprov-jdk18on:1.84"
    implementation "org.bouncycastle:bcpkix-jdk18on:1.84"

    testImplementation platform("org.junit:junit-bom:6.1.0")
    testImplementation "org.junit.jupiter:junit-jupiter"
    testRuntimeOnly "org.junit.platform:junit-platform-launcher"
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

test {
    useJUnitPlatform()
}

tasks.register("fatJar", Jar) {
    group = "build"
    description = "Builds the Burp-loadable single JAR extension."
    archiveClassifier = "all"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from sourceSets.main.output
    from {
        configurations.runtimeClasspath.collect { dependency ->
            dependency.isDirectory() ? dependency : zipTree(dependency)
        }
    }

    exclude "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA"

    manifest {
        attributes(
            "Implementation-Title": "Burp Python IDE Enhanced",
            "Implementation-Version": project.version
        )
    }
}

tasks.named("build") {
    dependsOn tasks.named("fatJar")
}
```

- [ ] **Step 4: Create `.gitignore`**

Create `.gitignore`:

```gitignore
.gradle/
build/
out/
.idea/
*.iml
graalpy.lock.tmp
```

- [ ] **Step 5: Create README**

Create `README.md`:

```markdown
# Burp Python IDE Enhanced

Single-JAR Burp Suite extension that embeds GraalPy and provides a Python IDE for pentest scripting.

## Build

```powershell
./gradlew.bat clean build
```

The Burp-loadable JAR is:

```text
build/libs/burp-python-ide-enhanced-0.1.0-all.jar
```
```

- [ ] **Step 6: Generate the Gradle wrapper**

Run:

```powershell
gradle wrapper --gradle-version 9.5.1
```

Expected:

```text
BUILD SUCCESSFUL
```

Expected files:

```text
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
```

- [ ] **Step 7: Create the Burp entry point**

Create `src/main/java/burp/BurpExtension.java`:

```java
package burp;

import burp.api.montoya.MontoyaApi;
import com.pythonburp.BurpPythonIdeExtension;

public final class BurpExtension implements burp.api.montoya.BurpExtension {
    private final BurpPythonIdeExtension extension = new BurpPythonIdeExtension();

    @Override
    public void initialize(MontoyaApi api) {
        extension.initialize(api);
    }
}
```

- [ ] **Step 8: Create extension metadata**

Create `src/main/java/com/pythonburp/core/VersionInfo.java`:

```java
package com.pythonburp.core;

public final class VersionInfo {
    public static final String EXTENSION_NAME = "Burp Python IDE Enhanced";
    public static final String EXTENSION_VERSION = "0.1.0";
    public static final String CATALOG_VERSION = "2026-06-10";
    public static final String GRAALPY_VERSION = "25.0.3";

    private VersionInfo() {
    }
}
```

- [ ] **Step 9: Create the extension initializer shell**

Create `src/main/java/com/pythonburp/BurpPythonIdeExtension.java`:

```java
package com.pythonburp;

import burp.api.montoya.MontoyaApi;
import com.pythonburp.core.VersionInfo;

public final class BurpPythonIdeExtension {
    public void initialize(MontoyaApi api) {
        api.extension().setName(VersionInfo.EXTENSION_NAME);
        api.logging().logToOutput(VersionInfo.EXTENSION_NAME + " " + VersionInfo.EXTENSION_VERSION + " loaded");
    }
}
```

- [ ] **Step 10: Lock GraalPy package dependencies**

Run:

```powershell
./gradlew.bat graalPyLockPackages
```

Expected:

```text
BUILD SUCCESSFUL
```

Expected file:

```text
graalpy.lock
```

- [ ] **Step 11: Run the first build**

Run:

```powershell
./gradlew.bat clean test fatJar
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 12: Commit the scaffold**

Run:

```powershell
git add settings.gradle build.gradle gradle.properties .gitignore README.md gradlew gradlew.bat gradle/wrapper graalpy.lock src/main/java
git commit -m "chore: scaffold Burp Python IDE project"
```

---

### Task 2: Lifecycle Context And Responsive Executors

**Files:**
- Create: `src/main/java/com/pythonburp/core/ExtensionContext.java`
- Create: `src/main/java/com/pythonburp/concurrency/Edt.java`
- Create: `src/main/java/com/pythonburp/concurrency/IdeExecutors.java`
- Create: `src/test/java/com/pythonburp/concurrency/IdeExecutorsTest.java`
- Modify: `src/main/java/com/pythonburp/BurpPythonIdeExtension.java`

- [ ] **Step 1: Write executor tests**

Create `src/test/java/com/pythonburp/concurrency/IdeExecutorsTest.java`:

```java
package com.pythonburp.concurrency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class IdeExecutorsTest {
    @Test
    void scriptExecutorRunsWorkOffCallingThread() throws Exception {
        try (IdeExecutors executors = new IdeExecutors(2)) {
            String callingThread = Thread.currentThread().getName();
            Future<String> future = executors.submitScript(() -> Thread.currentThread().getName());

            String workerThread = future.get(5, TimeUnit.SECONDS);

            assertFalse(workerThread.equals(callingThread));
            assertFalse(workerThread.isBlank());
        }
    }

    @Test
    void packageExecutorRunsWork() throws Exception {
        try (IdeExecutors executors = new IdeExecutors(1)) {
            Future<Integer> future = executors.submitPackageTask(() -> 42);

            assertEquals(42, future.get(5, TimeUnit.SECONDS));
        }
    }
}
```

- [ ] **Step 2: Run the failing executor tests**

Run:

```powershell
./gradlew.bat test --tests com.pythonburp.concurrency.IdeExecutorsTest
```

Expected:

```text
Compilation failed because IdeExecutors does not exist
```

- [ ] **Step 3: Implement EDT helper**

Create `src/main/java/com/pythonburp/concurrency/Edt.java`:

```java
package com.pythonburp.concurrency;

import javax.swing.SwingUtilities;

public final class Edt {
    private Edt() {
    }

    public static boolean isEdt() {
        return SwingUtilities.isEventDispatchThread();
    }

    public static void runLater(Runnable runnable) {
        SwingUtilities.invokeLater(runnable);
    }

    public static void requireEdt() {
        if (!isEdt()) {
            throw new IllegalStateException("Swing UI mutation must run on the Event Dispatch Thread");
        }
    }
}
```

- [ ] **Step 4: Implement executor service wrapper**

Create `src/main/java/com/pythonburp/concurrency/IdeExecutors.java`:

```java
package com.pythonburp.concurrency;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class IdeExecutors implements AutoCloseable {
    private final ExecutorService scriptExecutor;
    private final ExecutorService packageExecutor;

    public IdeExecutors(int maxScriptThreads) {
        int scriptThreads = Math.max(1, maxScriptThreads);
        this.scriptExecutor = Executors.newFixedThreadPool(scriptThreads, namedFactory("burp-python-script"));
        this.packageExecutor = Executors.newSingleThreadExecutor(namedFactory("burp-python-package"));
    }

    public <T> Future<T> submitScript(Callable<T> task) {
        return scriptExecutor.submit(Objects.requireNonNull(task, "task"));
    }

    public Future<?> submitScript(Runnable task) {
        return scriptExecutor.submit(Objects.requireNonNull(task, "task"));
    }

    public <T> Future<T> submitPackageTask(Callable<T> task) {
        return packageExecutor.submit(Objects.requireNonNull(task, "task"));
    }

    public Future<?> submitPackageTask(Runnable task) {
        return packageExecutor.submit(Objects.requireNonNull(task, "task"));
    }

    @Override
    public void close() {
        scriptExecutor.shutdownNow();
        packageExecutor.shutdownNow();
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger count = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
```

- [ ] **Step 5: Implement immutable extension context**

Create `src/main/java/com/pythonburp/core/ExtensionContext.java`:

```java
package com.pythonburp.core;

import burp.api.montoya.MontoyaApi;
import com.pythonburp.concurrency.IdeExecutors;

import java.util.Objects;

public final class ExtensionContext implements AutoCloseable {
    private final MontoyaApi api;
    private final IdeExecutors executors;

    public ExtensionContext(MontoyaApi api, IdeExecutors executors) {
        this.api = Objects.requireNonNull(api, "api");
        this.executors = Objects.requireNonNull(executors, "executors");
    }

    public MontoyaApi api() {
        return api;
    }

    public IdeExecutors executors() {
        return executors;
    }

    @Override
    public void close() {
        executors.close();
    }
}
```

- [ ] **Step 6: Wire executors into extension initializer**

Modify `src/main/java/com/pythonburp/BurpPythonIdeExtension.java`:

```java
package com.pythonburp;

import burp.api.montoya.MontoyaApi;
import com.pythonburp.concurrency.IdeExecutors;
import com.pythonburp.core.ExtensionContext;
import com.pythonburp.core.VersionInfo;

public final class BurpPythonIdeExtension {
    private ExtensionContext context;

    public void initialize(MontoyaApi api) {
        api.extension().setName(VersionInfo.EXTENSION_NAME);
        this.context = new ExtensionContext(api, new IdeExecutors(defaultScriptThreads()));
        api.logging().logToOutput(VersionInfo.EXTENSION_NAME + " " + VersionInfo.EXTENSION_VERSION + " loaded");
    }

    private int defaultScriptThreads() {
        int cpus = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(4, cpus - 1));
    }
}
```

- [ ] **Step 7: Run executor tests**

Run:

```powershell
./gradlew.bat test --tests com.pythonburp.concurrency.IdeExecutorsTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: Commit executor foundation**

Run:

```powershell
git add src/main/java/com/pythonburp/core src/main/java/com/pythonburp/concurrency src/main/java/com/pythonburp/BurpPythonIdeExtension.java src/test/java/com/pythonburp/concurrency
git commit -m "feat: add responsive executor foundation"
```

---

### Task 3: Bounded Console Buffer

**Files:**
- Create: `src/main/java/com/pythonburp/console/ConsoleEventType.java`
- Create: `src/main/java/com/pythonburp/console/ConsoleEvent.java`
- Create: `src/main/java/com/pythonburp/console/ConsoleBuffer.java`
- Create: `src/test/java/com/pythonburp/console/ConsoleBufferTest.java`

- [ ] **Step 1: Write console buffer tests**

Create `src/test/java/com/pythonburp/console/ConsoleBufferTest.java`:

```java
package com.pythonburp.console;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConsoleBufferTest {
    @Test
    void drainReturnsEventsInOrder() {
        ConsoleBuffer buffer = new ConsoleBuffer(10);
        buffer.append(ConsoleEventType.STDOUT, "one");
        buffer.append(ConsoleEventType.STDERR, "two");

        List<ConsoleEvent> events = buffer.drain();

        assertEquals("one", events.get(0).text());
        assertEquals(ConsoleEventType.STDERR, events.get(1).type());
        assertTrue(buffer.drain().isEmpty());
    }

    @Test
    void bufferDropsOldestEventsWhenCapacityIsExceeded() {
        ConsoleBuffer buffer = new ConsoleBuffer(2);
        buffer.append(ConsoleEventType.STDOUT, "one");
        buffer.append(ConsoleEventType.STDOUT, "two");
        buffer.append(ConsoleEventType.STDOUT, "three");

        List<ConsoleEvent> events = buffer.drain();

        assertEquals(2, events.size());
        assertEquals("two", events.get(0).text());
        assertEquals("three", events.get(1).text());
        assertEquals(1, buffer.droppedCount());
    }
}
```

- [ ] **Step 2: Run the failing console tests**

Run:

```powershell
./gradlew.bat test --tests com.pythonburp.console.ConsoleBufferTest
```

Expected:

```text
Compilation failed because ConsoleBuffer does not exist
```

- [ ] **Step 3: Implement console event types**

Create `src/main/java/com/pythonburp/console/ConsoleEventType.java`:

```java
package com.pythonburp.console;

public enum ConsoleEventType {
    STDOUT,
    STDERR,
    SYSTEM
}
```

- [ ] **Step 4: Implement console event record**

Create `src/main/java/com/pythonburp/console/ConsoleEvent.java`:

```java
package com.pythonburp.console;

import java.time.Instant;
import java.util.Objects;

public record ConsoleEvent(ConsoleEventType type, String text, Instant timestamp) {
    public ConsoleEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    public static ConsoleEvent now(ConsoleEventType type, String text) {
        return new ConsoleEvent(type, text, Instant.now());
    }
}
```

- [ ] **Step 5: Implement bounded console buffer**

Create `src/main/java/com/pythonburp/console/ConsoleBuffer.java`:

```java
package com.pythonburp.console;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

public final class ConsoleBuffer {
    private final int maxEvents;
    private final ConcurrentLinkedDeque<ConsoleEvent> events = new ConcurrentLinkedDeque<>();
    private final AtomicLong dropped = new AtomicLong();

    public ConsoleBuffer(int maxEvents) {
        if (maxEvents < 1) {
            throw new IllegalArgumentException("maxEvents must be positive");
        }
        this.maxEvents = maxEvents;
    }

    public void append(ConsoleEventType type, String text) {
        events.addLast(ConsoleEvent.now(type, text));
        while (events.size() > maxEvents) {
            ConsoleEvent removed = events.pollFirst();
            if (removed != null) {
                dropped.incrementAndGet();
            }
        }
    }

    public List<ConsoleEvent> drain() {
        List<ConsoleEvent> drained = new ArrayList<>();
        ConsoleEvent event;
        while ((event = events.pollFirst()) != null) {
            drained.add(event);
        }
        return drained;
    }

    public long droppedCount() {
        return dropped.get();
    }
}
```

- [ ] **Step 6: Run console tests**

Run:

```powershell
./gradlew.bat test --tests com.pythonburp.console.ConsoleBufferTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Commit console buffer**

Run:

```powershell
git add src/main/java/com/pythonburp/console src/test/java/com/pythonburp/console
git commit -m "feat: add bounded console buffer"
```

---

### Task 4: Cache Key And Resource Extraction

**Files:**
- Create: `src/main/java/com/pythonburp/cache/CacheKey.java`
- Create: `src/main/java/com/pythonburp/cache/CacheManager.java`
- Create: `src/test/java/com/pythonburp/cache/CacheManagerTest.java`

- [ ] **Step 1: Write cache manager tests**

Create `src/test/java/com/pythonburp/cache/CacheManagerTest.java`:

```java
package com.pythonburp.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CacheManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void cachePathIncludesVersionAndHash() throws Exception {
        CacheKey key = new CacheKey("0.1.0", "25.0.3", "windows", "amd64", "abc123");
        CacheManager manager = new CacheManager(tempDir);

        Path path = manager.prepareCache(key);

        assertTrue(Files.isDirectory(path));
        assertTrue(path.getFileName().toString().contains("0.1.0"));
        assertTrue(path.getFileName().toString().contains("abc123"));
    }

    @Test
    void extractBytesWritesFileAndVerifiesSha256() throws Exception {
        CacheKey key = new CacheKey("0.1.0", "25.0.3", "windows", "amd64", "hash");
        CacheManager manager = new CacheManager(tempDir);
        Path cache = manager.prepareCache(key);
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        String sha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

        Path file = manager.writeVerified(cache, "native/test.txt", content, sha256);

        assertEquals("hello", Files.readString(file));
    }
}
```

- [ ] **Step 2: Run failing cache tests**

Run:

```powershell
./gradlew.bat test --tests com.pythonburp.cache.CacheManagerTest
```

Expected:

```text
Compilation failed because CacheManager does not exist
```

- [ ] **Step 3: Implement cache key**

Create `src/main/java/com/pythonburp/cache/CacheKey.java`:

```java
package com.pythonburp.cache;

import java.util.Locale;
import java.util.Objects;

public record CacheKey(
    String extensionVersion,
    String graalPyVersion,
    String os,
    String arch,
    String catalogHash
) {
    public CacheKey {
        Objects.requireNonNull(extensionVersion, "extensionVersion");
        Objects.requireNonNull(graalPyVersion, "graalPyVersion");
        Objects.requireNonNull(os, "os");
        Objects.requireNonNull(arch, "arch");
        Objects.requireNonNull(catalogHash, "catalogHash");
    }

    public String directoryName() {
        return sanitize(extensionVersion + "-" + graalPyVersion + "-" + os + "-" + arch + "-" + catalogHash);
    }

    private static String sanitize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    }
}
```

- [ ] **Step 4: Implement cache manager**

Create `src/main/java/com/pythonburp/cache/CacheManager.java`:

```java
package com.pythonburp.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class CacheManager {
    private final Path root;

    public CacheManager(Path root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    public Path prepareCache(CacheKey key) throws IOException {
        Path cache = root.resolve(key.directoryName()).normalize();
        Files.createDirectories(cache);
        return cache;
    }

    public Path writeVerified(Path cache, String relativePath, byte[] content, String expectedSha256) throws IOException {
        Path target = cache.resolve(relativePath).normalize();
        if (!target.startsWith(cache.normalize())) {
            throw new IOException("Refusing to write outside cache: " + relativePath);
        }
        String actual = sha256(content);
        if (!actual.equalsIgnoreCase(expectedSha256)) {
            throw new IOException("SHA-256 mismatch for " + relativePath + ": expected " + expectedSha256 + " but got " + actual);
        }
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        return target;
    }

    public static Path defaultWindowsRoot() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = localAppData == null || localAppData.isBlank()
            ? Path.of(System.getProperty("user.home"), "AppData", "Local")
            : Path.of(localAppData);
        return base.resolve("BurpPythonIDE").resolve("cache");
    }

    private static String sha256(byte[] content) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }
}
```

- [ ] **Step 5: Run cache tests**

Run:

```powershell
./gradlew.bat test --tests com.pythonburp.cache.CacheManagerTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit cache manager**

Run:

```powershell
git add src/main/java/com/pythonburp/cache src/test/java/com/pythonburp/cache
git commit -m "feat: add verified extraction cache"
```

---

### Task 5: Package Catalog

**Files:**
- Create: `src/main/java/com/pythonburp/catalog/PackageCatalogEntry.java`
- Create: `src/main/java/com/pythonburp/catalog/PackageCatalog.java`
- Create: `src/main/java/com/pythonburp/catalog/PackageCatalogLoader.java`
- Create: `src/main/resources/package-catalog.json`
- Create: `src/test/java/com/pythonburp/catalog/PackageCatalogLoaderTest.java`

- [ ] **Step 1: Write catalog loader test**

Create `src/test/java/com/pythonburp/catalog/PackageCatalogLoaderTest.java`:

```java
package com.pythonburp.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PackageCatalogLoaderTest {
    @Test
    void loadsBundledCatalog() throws Exception {
        PackageCatalog catalog = PackageCatalogLoader.loadBundled();

        assertTrue(catalog.find("beautifulsoup4").isPresent());
        assertEquals("java-backed", catalog.find("burp.crypto").orElseThrow().tier());
    }
}
```

- [ ] **Step 2: Run failing catalog test**

Run:

```powershell
./gradlew.bat test --tests com.pythonburp.catalog.PackageCatalogLoaderTest
```

Expected:

```text
Compilation failed because PackageCatalogLoader does not exist
```

- [ ] **Step 3: Create package catalog resource**

Create `src/main/resources/package-catalog.json`:

```json
[
  {
    "name": "burp.crypto",
    "version": "0.1.0",
    "tier": "java-backed",
    "nativeRequired": false,
    "smokeTest": "from burp import crypto; print(crypto.sha256_hex(b'abc'))"
  },
  {
    "name": "burp.encoder",
    "version": "0.1.0",
    "tier": "java-backed",
    "nativeRequired": false,
    "smokeTest": "from burp import encoder; print(encoder.base64_encode(b'abc'))"
  },
  {
    "name": "beautifulsoup4",
    "version": "locked-by-graalpy",
    "tier": "pure-python",
    "nativeRequired": false,
    "smokeTest": "import bs4; print(bs4.__name__)"
  },
  {
    "name": "html5lib",
    "version": "locked-by-graalpy",
    "tier": "pure-python",
    "nativeRequired": false,
    "smokeTest": "import html5lib; print(html5lib.__name__)"
  },
  {
    "name": "pyjwt",
    "version": "locked-by-graalpy",
    "tier": "tested-graalpy",
    "nativeRequired": false,
    "smokeTest": "import jwt; print(jwt.__name__)"
  }
]
```

- [ ] **Step 4: Implement catalog entry**

Create `src/main/java/com/pythonburp/catalog/PackageCatalogEntry.java`:

```java
package com.pythonburp.catalog;

public record PackageCatalogEntry(
    String name,
    String version,
    String tier,
    boolean nativeRequired,
    String smokeTest
) {
}
```

- [ ] **Step 5: Implement catalog collection**

Create `src/main/java/com/pythonburp/catalog/PackageCatalog.java`:

```java
package com.pythonburp.catalog;

import java.util.List;
import java.util.Optional;

public record PackageCatalog(List<PackageCatalogEntry> entries) {
    public Optional<PackageCatalogEntry> find(String name) {
        return entries.stream()
            .filter(entry -> entry.name().equalsIgnoreCase(name))
            .findFirst();
    }
}
```

- [ ] **Step 6: Implement catalog loader**

Create `src/main/java/com/pythonburp/catalog/PackageCatalogLoader.java`:

```java
package com.pythonburp.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PackageCatalogLoader {
    private static final Pattern OBJECT = Pattern.compile("\\{([^}]*)}");

    private PackageCatalogLoader() {
    }

    public static PackageCatalog loadBundled() throws IOException {
        try (InputStream stream = PackageCatalogLoader.class.getResourceAsStream("/package-catalog.json")) {
            if (stream == null) {
                throw new IOException("Missing /package-catalog.json");
            }
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    static PackageCatalog parse(String json) {
        List<PackageCatalogEntry> entries = new ArrayList<>();
        Matcher matcher = OBJECT.matcher(json);
        while (matcher.find()) {
            String object = matcher.group(1);
            entries.add(new PackageCatalogEntry(
                stringValue(object, "name"),
                stringValue(object, "version"),
                stringValue(object, "tier"),
                booleanValue(object, "nativeRequired"),
                stringValue(object, "smokeTest")
            ));
        }
        return new PackageCatalog(List.copyOf(entries));
    }

    private static String stringValue(String object, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(object);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing string key: " + key);
        }
        return matcher.group(1);
    }

    private static boolean booleanValue(String object, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)");
        Matcher matcher = pattern.matcher(object);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing boolean key: " + key);
        }
        return Boolean.parseBoolean(matcher.group(1));
    }
}
```

- [ ] **Step 7: Run catalog tests**

Run:

```powershell
./gradlew.bat test --tests com.pythonburp.catalog.PackageCatalogLoaderTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: Commit package catalog**

Run:

```powershell
git add src/main/java/com/pythonburp/catalog src/main/resources/package-catalog.json src/test/java/com/pythonburp/catalog
git commit -m "feat: add bundled package catalog"
```

---

### Task 6: Java-Backed Encoder And Crypto Bridge

**Files:**
- Create: `src/main/java/com/pythonburp/bridge/EncoderBridge.java`
- Create: `src/main/java/com/pythonburp/bridge/CryptoBridge.java`
- Create: `src/main/java/com/pythonburp/bridge/BurpBridge.java`
- Create: `src/test/java/com/pythonburp/bridge/EncoderBridgeTest.java`
- Create: `src/test/java/com/pythonburp/bridge/CryptoBridgeTest.java`

- [ ] **Step 1: Write encoder bridge test**

Create `src/test/java/com/pythonburp/bridge/EncoderBridgeTest.java`:

```java
package com.pythonburp.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class EncoderBridgeTest {
    @Test
    void base64RoundTripsBytes() {
        EncoderBridge encoder = new EncoderBridge();

        String encoded = encoder.base64Encode("abc".getBytes());

        assertEquals("YWJj", encoded);
        assertArrayEquals("abc".getBytes(), encoder.base64Decode(encoded));
    }
}
```

- [ ] **Step 2: Write crypto bridge test**

Create `src/test/java/com/pythonburp/bridge/CryptoBridgeTest.java`:

```java
package com.pythonburp.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CryptoBridgeTest {
    @Test
    void sha256HexMatchesKnownValue() {
        CryptoBridge crypto = new CryptoBridge();

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            crypto.sha256Hex("abc".getBytes())
        );
    }
}
```

- [ ] **Step 3: Run failing bridge tests**

Run:

```powershell
./gradlew.bat test --tests com.pythonburp.bridge.EncoderBridgeTest --tests com.pythonburp.bridge.CryptoBridgeTest
```

Expected:

```text
Compilation failed because EncoderBridge and CryptoBridge do not exist
```

- [ ] **Step 4: Implement encoder bridge**

Create `src/main/java/com/pythonburp/bridge/EncoderBridge.java`:

```java
package com.pythonburp.bridge;

import java.util.Base64;

public final class EncoderBridge {
    public String base64Encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public byte[] base64Decode(String value) {
        return Base64.getDecoder().decode(value);
    }
}
```

- [ ] **Step 5: Implement crypto bridge**

Create `src/main/java/com/pythonburp/bridge/CryptoBridge.java`:

```java
package com.pythonburp.bridge;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class CryptoBridge {
    public String sha256Hex(byte[] data) {
        return digestHex("SHA-256", data);
    }

    public String sha1Hex(byte[] data) {
        return digestHex("SHA-1", data);
    }

    private String digestHex(String algorithm, byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " is unavailable", e);
        }
    }
}
```

- [ ] **Step 6: Implement bridge root**

Create `src/main/java/com/pythonburp/bridge/BurpBridge.java`:

```java
package com.pythonburp.bridge;

public final class BurpBridge {
    private final EncoderBridge encoder = new EncoderBridge();
    private final CryptoBridge crypto = new CryptoBridge();

    public EncoderBridge encoder() {
        return encoder;
    }

    public CryptoBridge crypto() {
        return crypto;
    }
}
```

- [ ] **Step 7: Run bridge tests**

Run:

```powershell
./gradlew.bat test --tests com.pythonburp.bridge.EncoderBridgeTest --tests com.pythonburp.bridge.CryptoBridgeTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: Commit bridge helpers**

Run:

```powershell
git add src/main/java/com/pythonburp/bridge src/test/java/com/pythonburp/bridge
git commit -m "feat: add Java-backed encoder and crypto helpers"
```

---

### Task 7: Python Wrapper Modules In GraalPy VFS

**Files:**
- Create: `src/main/resources/GRAALPY-VFS/com.pythonburp/burp-python-ide/src/burp/__init__.py`
- Create: `src/main/resources/GRAALPY-VFS/com.pythonburp/burp-python-ide/src/burp/encoder.py`
- Create: `src/main/resources/GRAALPY-VFS/com.pythonburp/burp-python-ide/src/burp/crypto.py`

- [ ] **Step 1: Create Python package init**

Create `src/main/resources/GRAALPY-VFS/com.pythonburp/burp-python-ide/src/burp/__init__.py`:

```python
from . import encoder
from . import crypto

__all__ = ["encoder", "crypto"]
```

- [ ] **Step 2: Create encoder wrapper**

Create `src/main/resources/GRAALPY-VFS/com.pythonburp/burp-python-ide/src/burp/encoder.py`:

```python
def _bridge():
    return burpBridge.encoder()


def base64_encode(data):
    if isinstance(data, str):
        data = data.encode("utf-8")
    return _bridge().base64Encode(data)


def base64_decode(value):
    return bytes(_bridge().base64Decode(value))
```

- [ ] **Step 3: Create crypto wrapper**

Create `src/main/resources/GRAALPY-VFS/com.pythonburp/burp-python-ide/src/burp/crypto.py`:

```python
def _bridge():
    return burpBridge.crypto()


def sha256_hex(data):
    if isinstance(data, str):
        data = data.encode("utf-8")
    return _bridge().sha256Hex(data)


def sha1_hex(data):
    if isinstance(data, str):
        data = data.encode("utf-8")
    return _bridge().sha1Hex(data)
```

- [ ] **Step 4: Run resource packaging build**

Run:

```powershell
./gradlew.bat processResources
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit Python wrapper modules**

Run:

```powershell
git add src/main/resources/GRAALPY-VFS
git commit -m "feat: add Python wrapper modules for Java bridge"
```

---

### Task 8: GraalPy Runtime With Full Access

**Files:**
- Create: `src/main/java/com/pythonburp/python/PythonRuntime.java`
- Create: `src/main/java/com/pythonburp/python/GraalPyPythonRuntime.java`
- Create: `src/test/java/com/pythonburp/python/GraalPyPythonRuntimeTest.java`

- [ ] **Step 1: Write GraalPy runtime tests**

Create `src/test/java/com/pythonburp/python/GraalPyPythonRuntimeTest.java`:

```java
package com.pythonburp.python;

import com.pythonburp.bridge.BurpBridge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GraalPyPythonRuntimeTest {
    @Test
    void evaluatesSimplePythonExpression() throws Exception {
        try (PythonRuntime runtime = new GraalPyPythonRuntime(new BurpBridge())) {
            ScriptRunResult result = runtime.execute("print(1 + 2)");

            assertEquals(ScriptStatus.SUCCEEDED, result.status());
            assertTrue(result.stdout().contains("3"));
        }
    }

    @Test
    void exposesJavaBackedBurpModules() throws Exception {
        try (PythonRuntime runtime = new GraalPyPythonRuntime(new BurpBridge())) {
            ScriptRunResult result = runtime.execute("from burp import crypto\nprint(crypto.sha256_hex(b'abc'))");

            assertEquals(ScriptStatus.SUCCEEDED, result.status());
            assertTrue(result.stdout().contains("ba7816bf8f01cfea"));
        }
    }
}
```

- [ ] **Step 2: Run failing GraalPy runtime tests**

Run:

```powershell
./gradlew.bat test --tests com.pythonburp.python.GraalPyPythonRuntimeTest
```

Expected:

```text
Compilation failed because PythonRuntime does not exist
```

- [ ] **Step 3: Create script status enum**

Create `src/main/java/com/pythonburp/python/ScriptStatus.java`:

```java
package com.pythonburp.python;

public enum ScriptStatus {
    SUCCEEDED,
    FAILED,
    CANCELLED
}
```

- [ ] **Step 4: Create script result record**

Create `src/main/java/com/pythonburp/python/ScriptRunResult.java`:

```java
package com.pythonburp.python;

public record ScriptRunResult(
    ScriptStatus status,
    String stdout,
    String stderr,
    String errorMessage
) {
    public static ScriptRunResult succeeded(String stdout, String stderr) {
        return new ScriptRunResult(ScriptStatus.SUCCEEDED, stdout, stderr, "");
    }

    public static ScriptRunResult failed(String stdout, String stderr, String errorMessage) {
        return new ScriptRunResult(ScriptStatus.FAILED, stdout, stderr, errorMessage);
    }
}
```

- [ ] **Step 5: Create Python runtime interface**

Create `src/main/java/com/pythonburp/python/PythonRuntime.java`:

```java
package com.pythonburp.python;

public interface PythonRuntime extends AutoCloseable {
    ScriptRunResult execute(String source);

    @Override
    void close();
}
```

- [ ] **Step 6: Implement GraalPy runtime**

Create `src/main/java/com/pythonburp/python/GraalPyPythonRuntime.java`:

```java
package com.pythonburp.python;

import com.pythonburp.bridge.BurpBridge;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.python.embedding.GraalPyResources;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class GraalPyPythonRuntime implements PythonRuntime {
    private final BurpBridge bridge;

    public GraalPyPythonRuntime(BurpBridge bridge) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
    }

    @Override
    public ScriptRunResult execute(String source) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try (Context context = GraalPyResources.contextBuilder()
            .allowAllAccess(true)
            .allowIO(IOAccess.ALL)
            .out(stdout)
            .err(stderr)
            .build()) {
            context.getBindings("python").putMember("burpBridge", bridge);
            context.eval("python", "import builtins\nbuiltins.burpBridge = burpBridge");
            context.eval("python", source);
            return ScriptRunResult.succeeded(text(stdout), text(stderr));
        } catch (PolyglotException | RuntimeException e) {
            return ScriptRunResult.failed(text(stdout), text(stderr), e.toString());
        }
    }

    @Override
    public void close() {
    }

    private static String text(ByteArrayOutputStream stream) {
        return stream.toString(StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 7: Run GraalPy runtime tests**

Run:

```powershell
./gradlew.bat test --tests com.pythonburp.python.GraalPyPythonRuntimeTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: Commit GraalPy runtime**

Run:

```powershell
git add src/main/java/com/pythonburp/python src/test/java/com/pythonburp/python
git commit -m "feat: add full-access GraalPy runtime"
```

---

### Task 9: Background Script Executor

**Files:**
- Create: `src/main/java/com/pythonburp/python/ScriptRunRequest.java`
- Create: `src/main/java/com/pythonburp/python/ScriptExecutor.java`
- Create: `src/test/java/com/pythonburp/python/ScriptExecutorTest.java`

- [ ] **Step 1: Write script executor test**

Create `src/test/java/com/pythonburp/python/ScriptExecutorTest.java`:

```java
package com.pythonburp.python;

import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.concurrency.IdeExecutors;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ScriptExecutorTest {
    @Test
    void runReturnsImmediatelyWithFuture() throws Exception {
        try (IdeExecutors executors = new IdeExecutors(1)) {
            ScriptExecutor scriptExecutor = new ScriptExecutor(executors, () -> new GraalPyPythonRuntime(new BurpBridge()));
            ScriptRunRequest request = new ScriptRunRequest("print('ok')", Duration.ofSeconds(10));

            Future<ScriptRunResult> future = scriptExecutor.run(request);

            assertFalse(future.isDone() && Thread.currentThread().getName().startsWith("AWT-EventQueue"));
            assertEquals(ScriptStatus.SUCCEEDED, future.get().status());
        }
    }
}
```

- [ ] **Step 2: Run failing script executor test**

Run:

```powershell
./gradlew.bat test --tests com.pythonburp.python.ScriptExecutorTest
```

Expected:

```text
Compilation failed because ScriptExecutor does not exist
```

- [ ] **Step 3: Implement script request**

Create `src/main/java/com/pythonburp/python/ScriptRunRequest.java`:

```java
package com.pythonburp.python;

import java.time.Duration;
import java.util.Objects;

public record ScriptRunRequest(String source, Duration timeout) {
    public ScriptRunRequest {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(timeout, "timeout");
    }
}
```

- [ ] **Step 4: Implement script executor**

Create `src/main/java/com/pythonburp/python/ScriptExecutor.java`:

```java
package com.pythonburp.python;

import com.pythonburp.concurrency.IdeExecutors;

import java.util.Objects;
import java.util.concurrent.Future;
import java.util.function.Supplier;

public final class ScriptExecutor {
    private final IdeExecutors executors;
    private final Supplier<PythonRuntime> runtimeFactory;

    public ScriptExecutor(IdeExecutors executors, Supplier<PythonRuntime> runtimeFactory) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
    }

    public Future<ScriptRunResult> run(ScriptRunRequest request) {
        Objects.requireNonNull(request, "request");
        return executors.submitScript(() -> {
            try (PythonRuntime runtime = runtimeFactory.get()) {
                return runtime.execute(request.source());
            }
        });
    }
}
```

- [ ] **Step 5: Run script executor test**

Run:

```powershell
./gradlew.bat test --tests com.pythonburp.python.ScriptExecutorTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit script executor**

Run:

```powershell
git add src/main/java/com/pythonburp/python/ScriptRunRequest.java src/main/java/com/pythonburp/python/ScriptExecutor.java src/test/java/com/pythonburp/python/ScriptExecutorTest.java
git commit -m "feat: run scripts on background executor"
```

---

### Task 10: Swing IDE Skeleton

**Files:**
- Create: `src/main/java/com/pythonburp/ui/BurpPythonIdeTab.java`
- Create: `src/main/java/com/pythonburp/ui/EditorPanel.java`
- Create: `src/main/java/com/pythonburp/ui/ConsolePanel.java`
- Create: `src/main/java/com/pythonburp/ui/PackageCatalogPanel.java`
- Create: `src/main/java/com/pythonburp/ui/StatusBar.java`
- Modify: `src/main/java/com/pythonburp/BurpPythonIdeExtension.java`

- [ ] **Step 1: Create editor panel**

Create `src/main/java/com/pythonburp/ui/EditorPanel.java`:

```java
package com.pythonburp.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.JPanel;
import java.awt.BorderLayout;

public final class EditorPanel extends JPanel {
    private final RSyntaxTextArea editor = new RSyntaxTextArea();

    public EditorPanel() {
        super(new BorderLayout());
        editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
        editor.setCodeFoldingEnabled(true);
        editor.setText("print('Hello from Burp Python IDE')\n");
        add(new RTextScrollPane(editor), BorderLayout.CENTER);
    }

    public String source() {
        return editor.getText();
    }
}
```

- [ ] **Step 2: Create console panel**

Create `src/main/java/com/pythonburp/ui/ConsolePanel.java`:

```java
package com.pythonburp.ui;

import com.pythonburp.console.ConsoleEvent;
import com.pythonburp.console.ConsoleEventType;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.util.List;

public final class ConsolePanel extends JPanel {
    private final JTextArea output = new JTextArea();

    public ConsolePanel() {
        super(new BorderLayout());
        output.setEditable(false);
        add(new JScrollPane(output), BorderLayout.CENTER);
    }

    public void append(List<ConsoleEvent> events) {
        for (ConsoleEvent event : events) {
            String prefix = event.type() == ConsoleEventType.STDERR ? "[err] " : "";
            output.append(prefix + event.text());
            if (!event.text().endsWith("\n")) {
                output.append("\n");
            }
        }
    }

    public void appendSystem(String text) {
        output.append("[system] " + text + "\n");
    }
}
```

- [ ] **Step 3: Create package catalog panel**

Create `src/main/java/com/pythonburp/ui/PackageCatalogPanel.java`:

```java
package com.pythonburp.ui;

import com.pythonburp.catalog.PackageCatalog;
import com.pythonburp.catalog.PackageCatalogEntry;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.awt.BorderLayout;

public final class PackageCatalogPanel extends JPanel {
    public PackageCatalogPanel(PackageCatalog catalog) {
        super(new BorderLayout());
        String[] columns = {"Package", "Version", "Tier", "Native"};
        Object[][] rows = catalog.entries().stream()
            .map(this::row)
            .toArray(Object[][]::new);
        add(new JScrollPane(new JTable(rows, columns)), BorderLayout.CENTER);
    }

    private Object[] row(PackageCatalogEntry entry) {
        return new Object[] {entry.name(), entry.version(), entry.tier(), entry.nativeRequired()};
    }
}
```

- [ ] **Step 4: Create status bar**

Create `src/main/java/com/pythonburp/ui/StatusBar.java`:

```java
package com.pythonburp.ui;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

public final class StatusBar extends JPanel {
    private final JLabel label = new JLabel("Ready");

    public StatusBar() {
        super(new BorderLayout());
        add(label, BorderLayout.CENTER);
    }

    public void setStatus(String status) {
        label.setText(status);
    }
}
```

- [ ] **Step 5: Create IDE tab panel**

Create `src/main/java/com/pythonburp/ui/BurpPythonIdeTab.java`:

```java
package com.pythonburp.ui;

import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.catalog.PackageCatalog;
import com.pythonburp.concurrency.Edt;
import com.pythonburp.console.ConsoleEvent;
import com.pythonburp.console.ConsoleEventType;
import com.pythonburp.python.GraalPyPythonRuntime;
import com.pythonburp.python.ScriptExecutor;
import com.pythonburp.python.ScriptRunRequest;
import com.pythonburp.python.ScriptRunResult;
import com.pythonburp.python.ScriptStatus;
import com.pythonburp.concurrency.IdeExecutors;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import java.awt.BorderLayout;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Future;

public final class BurpPythonIdeTab extends JPanel {
    private final EditorPanel editor = new EditorPanel();
    private final ConsolePanel console = new ConsolePanel();
    private final StatusBar statusBar = new StatusBar();
    private final ScriptExecutor scriptExecutor;
    private Future<ScriptRunResult> activeRun;

    public BurpPythonIdeTab(IdeExecutors executors, PackageCatalog catalog, BurpBridge bridge) {
        super(new BorderLayout());
        this.scriptExecutor = new ScriptExecutor(executors, () -> new GraalPyPythonRuntime(bridge));

        JButton run = new JButton("Run");
        JButton stop = new JButton("Stop");
        run.addActionListener(event -> runScript());
        stop.addActionListener(event -> stopScript());

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(run);
        toolbar.add(stop);

        JTabbedPane right = new JTabbedPane();
        right.addTab("Packages", new PackageCatalogPanel(catalog));

        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editor, right);
        center.setResizeWeight(0.75);

        JSplitPane main = new JSplitPane(JSplitPane.VERTICAL_SPLIT, center, console);
        main.setResizeWeight(0.7);

        add(toolbar, BorderLayout.NORTH);
        add(main, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
    }

    private void runScript() {
        Edt.requireEdt();
        statusBar.setStatus("Running");
        console.appendSystem("Running script");
        activeRun = scriptExecutor.run(new ScriptRunRequest(editor.source(), Duration.ofMinutes(5)));
        Thread waiter = new Thread(() -> {
            try {
                ScriptRunResult result = activeRun.get();
                Edt.runLater(() -> publishResult(result));
            } catch (Exception e) {
                Edt.runLater(() -> {
                    statusBar.setStatus("Failed");
                    console.append(List.of(ConsoleEvent.now(ConsoleEventType.STDERR, e.toString())));
                });
            }
        }, "burp-python-ui-result-waiter");
        waiter.setDaemon(true);
        waiter.start();
    }

    private void stopScript() {
        Edt.requireEdt();
        if (activeRun != null) {
            activeRun.cancel(true);
            statusBar.setStatus("Stopping");
        }
    }

    private void publishResult(ScriptRunResult result) {
        Edt.requireEdt();
        if (!result.stdout().isBlank()) {
            console.append(List.of(ConsoleEvent.now(ConsoleEventType.STDOUT, result.stdout())));
        }
        if (!result.stderr().isBlank()) {
            console.append(List.of(ConsoleEvent.now(ConsoleEventType.STDERR, result.stderr())));
        }
        if (!result.errorMessage().isBlank()) {
            console.append(List.of(ConsoleEvent.now(ConsoleEventType.STDERR, result.errorMessage())));
        }
        statusBar.setStatus(result.status() == ScriptStatus.SUCCEEDED ? "Ready" : "Failed");
    }
}
```

- [ ] **Step 6: Register the UI tab in Burp**

Modify `src/main/java/com/pythonburp/BurpPythonIdeExtension.java`:

```java
package com.pythonburp;

import burp.api.montoya.MontoyaApi;
import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.catalog.PackageCatalog;
import com.pythonburp.catalog.PackageCatalogLoader;
import com.pythonburp.concurrency.IdeExecutors;
import com.pythonburp.core.ExtensionContext;
import com.pythonburp.core.VersionInfo;
import com.pythonburp.ui.BurpPythonIdeTab;

public final class BurpPythonIdeExtension {
    private ExtensionContext context;

    public void initialize(MontoyaApi api) {
        api.extension().setName(VersionInfo.EXTENSION_NAME);
        this.context = new ExtensionContext(api, new IdeExecutors(defaultScriptThreads()));
        PackageCatalog catalog = loadCatalog(api);
        BurpPythonIdeTab tab = new BurpPythonIdeTab(context.executors(), catalog, new BurpBridge());
        api.userInterface().registerSuiteTab("Python IDE", tab);
        api.logging().logToOutput(VersionInfo.EXTENSION_NAME + " " + VersionInfo.EXTENSION_VERSION + " loaded");
    }

    private PackageCatalog loadCatalog(MontoyaApi api) {
        try {
            return PackageCatalogLoader.loadBundled();
        } catch (Exception e) {
            api.logging().logToError("Failed to load package catalog: " + e);
            return new PackageCatalog(java.util.List.of());
        }
    }

    private int defaultScriptThreads() {
        int cpus = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(4, cpus - 1));
    }
}
```

- [ ] **Step 7: Build the UI skeleton**

Run:

```powershell
./gradlew.bat clean test fatJar
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: Commit UI skeleton**

Run:

```powershell
git add src/main/java/com/pythonburp/ui src/main/java/com/pythonburp/BurpPythonIdeExtension.java
git commit -m "feat: add responsive Swing IDE skeleton"
```

---

### Task 11: Package Smoke Test Task

**Files:**
- Modify: `build.gradle`
- Create: `src/test/java/com/pythonburp/catalog/PackageSmokeTest.java`

- [ ] **Step 1: Write package smoke test**

Create `src/test/java/com/pythonburp/catalog/PackageSmokeTest.java`:

```java
package com.pythonburp.catalog;

import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.python.GraalPyPythonRuntime;
import com.pythonburp.python.PythonRuntime;
import com.pythonburp.python.ScriptRunResult;
import com.pythonburp.python.ScriptStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PackageSmokeTest {
    @Test
    void bundledSmokeTestsPass() throws Exception {
        PackageCatalog catalog = PackageCatalogLoader.loadBundled();
        try (PythonRuntime runtime = new GraalPyPythonRuntime(new BurpBridge())) {
            for (PackageCatalogEntry entry : catalog.entries()) {
                ScriptRunResult result = runtime.execute(entry.smokeTest());
                assertEquals(ScriptStatus.SUCCEEDED, result.status(), entry.name() + " failed: " + result.errorMessage());
            }
        }
    }
}
```

- [ ] **Step 2: Run package smoke test**

Run:

```powershell
./gradlew.bat test --tests com.pythonburp.catalog.PackageSmokeTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Add a named smoke-test task**

Modify `build.gradle` and add this block after the `test { ... }` block:

```groovy
tasks.register("packageSmokeTest", Test) {
    description = "Runs GraalPy import and package compatibility smoke tests."
    group = "verification"
    useJUnitPlatform()
    include "**/PackageSmokeTest.class"
    shouldRunAfter test
}

check {
    dependsOn tasks.named("packageSmokeTest")
}
```

- [ ] **Step 4: Run named package smoke test task**

Run:

```powershell
./gradlew.bat packageSmokeTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit package smoke tests**

Run:

```powershell
git add build.gradle src/test/java/com/pythonburp/catalog/PackageSmokeTest.java
git commit -m "test: add package compatibility smoke tests"
```

---

### Task 12: HTTP Bridge Interface And Montoya Adapter

**Files:**
- Create: `src/main/java/com/pythonburp/bridge/HttpBridge.java`
- Create: `src/main/java/com/pythonburp/bridge/MontoyaHttpBridge.java`
- Modify: `src/main/java/com/pythonburp/bridge/BurpBridge.java`
- Modify: `src/main/java/com/pythonburp/BurpPythonIdeExtension.java`
- Modify: `src/main/resources/GRAALPY-VFS/com.pythonburp/burp-python-ide/src/burp/__init__.py`

- [ ] **Step 1: Create HTTP bridge interface**

Create `src/main/java/com/pythonburp/bridge/HttpBridge.java`:

```java
package com.pythonburp.bridge;

public interface HttpBridge {
    HttpResult send(String method, String url, String body);

    record HttpResult(int statusCode, String body) {
    }

    static HttpBridge unavailable() {
        return (method, url, body) -> new HttpResult(0, "HTTP bridge is not connected to Burp yet");
    }
}
```

- [ ] **Step 2: Create Montoya HTTP adapter**

Create `src/main/java/com/pythonburp/bridge/MontoyaHttpBridge.java`:

```java
package com.pythonburp.bridge;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.util.Objects;

import static burp.api.montoya.http.message.requests.HttpRequest.httpRequestFromUrl;

public final class MontoyaHttpBridge implements HttpBridge {
    private final MontoyaApi api;

    public MontoyaHttpBridge(MontoyaApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    @Override
    public HttpResult send(String method, String url, String body) {
        HttpRequest request = httpRequestFromUrl(url)
            .withMethod(method)
            .withBody(body == null ? "" : body)
            .withDefaultHeaders();
        HttpRequestResponse requestResponse = api.http().sendRequest(request);
        HttpResponse response = requestResponse.response();
        if (response == null) {
            return new HttpResult(0, "");
        }
        return new HttpResult(response.statusCode(), response.bodyToString());
    }
}
```

- [ ] **Step 3: Add HTTP bridge to bridge root**

Modify `src/main/java/com/pythonburp/bridge/BurpBridge.java`:

```java
package com.pythonburp.bridge;

public final class BurpBridge {
    private final EncoderBridge encoder = new EncoderBridge();
    private final CryptoBridge crypto = new CryptoBridge();
    private final HttpBridge http;

    public BurpBridge() {
        this(HttpBridge.unavailable());
    }

    public BurpBridge(HttpBridge http) {
        this.http = http;
    }

    public EncoderBridge encoder() {
        return encoder;
    }

    public CryptoBridge crypto() {
        return crypto;
    }

    public HttpBridge http() {
        return http;
    }
}
```

- [ ] **Step 4: Wire the Montoya HTTP adapter into the UI runtime**

Modify `src/main/java/com/pythonburp/BurpPythonIdeExtension.java`:

```java
package com.pythonburp;

import burp.api.montoya.MontoyaApi;
import com.pythonburp.bridge.BurpBridge;
import com.pythonburp.bridge.MontoyaHttpBridge;
import com.pythonburp.catalog.PackageCatalog;
import com.pythonburp.catalog.PackageCatalogLoader;
import com.pythonburp.concurrency.IdeExecutors;
import com.pythonburp.core.ExtensionContext;
import com.pythonburp.core.VersionInfo;
import com.pythonburp.ui.BurpPythonIdeTab;

public final class BurpPythonIdeExtension {
    private ExtensionContext context;

    public void initialize(MontoyaApi api) {
        api.extension().setName(VersionInfo.EXTENSION_NAME);
        this.context = new ExtensionContext(api, new IdeExecutors(defaultScriptThreads()));
        PackageCatalog catalog = loadCatalog(api);
        BurpBridge bridge = new BurpBridge(new MontoyaHttpBridge(api));
        BurpPythonIdeTab tab = new BurpPythonIdeTab(context.executors(), catalog, bridge);
        api.userInterface().registerSuiteTab("Python IDE", tab);
        api.logging().logToOutput(VersionInfo.EXTENSION_NAME + " " + VersionInfo.EXTENSION_VERSION + " loaded");
    }

    private PackageCatalog loadCatalog(MontoyaApi api) {
        try {
            return PackageCatalogLoader.loadBundled();
        } catch (Exception e) {
            api.logging().logToError("Failed to load package catalog: " + e);
            return new PackageCatalog(java.util.List.of());
        }
    }

    private int defaultScriptThreads() {
        int cpus = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(4, cpus - 1));
    }
}
```

- [ ] **Step 5: Export HTTP wrapper from Python package**

Modify `src/main/resources/GRAALPY-VFS/com.pythonburp/burp-python-ide/src/burp/__init__.py`:

```python
from . import encoder
from . import crypto


class _Http:
    def send(self, method, url, body=""):
        return burpBridge.http().send(method, url, body)


http = _Http()

__all__ = ["encoder", "crypto", "http"]
```

- [ ] **Step 6: Run tests**

Run:

```powershell
./gradlew.bat test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Commit HTTP bridge adapter**

Run:

```powershell
git add src/main/java/com/pythonburp/bridge/HttpBridge.java src/main/java/com/pythonburp/bridge/MontoyaHttpBridge.java src/main/java/com/pythonburp/bridge/BurpBridge.java src/main/java/com/pythonburp/BurpPythonIdeExtension.java src/main/resources/GRAALPY-VFS/com.pythonburp/burp-python-ide/src/burp/__init__.py
git commit -m "feat: route Python HTTP helper through Montoya"
```

---

### Task 13: Final Build Verification And Manual Burp Smoke Checklist

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update README with manual verification**

Modify `README.md`:

```markdown
# Burp Python IDE Enhanced

Single-JAR Burp Suite extension that embeds GraalPy and provides a Python IDE for pentest scripting.

## Build

```powershell
./gradlew.bat clean build
```

The Burp-loadable JAR is:

```text
build/libs/burp-python-ide-enhanced-0.1.0-all.jar
```

## Manual Burp Smoke Test

1. Open Burp Suite.
2. Go to Extensions > Installed > Add.
3. Select Extension type: Java.
4. Select `build/libs/burp-python-ide-enhanced-0.1.0-all.jar`.
5. Confirm a `Python IDE` suite tab appears.
6. Run:

```python
from burp import encoder, crypto

print(encoder.base64_encode(b"abc"))
print(crypto.sha256_hex(b"abc"))
```

7. Confirm the console prints `YWJj` and the SHA-256 hash.
8. While the script runs, switch Burp tabs and confirm Burp remains responsive.
```

- [ ] **Step 2: Run full verification**

Run:

```powershell
./gradlew.bat clean build packageSmokeTest fatJar
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Confirm the JAR exists**

Run:

```powershell
Get-ChildItem -Path build/libs -Filter '*-all.jar'
```

Expected:

```text
burp-python-ide-enhanced-0.1.0-all.jar
```

- [ ] **Step 4: Commit final verification docs**

Run:

```powershell
git add README.md
git commit -m "docs: add Burp smoke test checklist"
```

---

## Self-Review Checklist

- Spec goal covered: single installable JAR, no system Python, no Jython, no portable CPython folder.
- Enhanced mode covered: GraalPy VFS resources, package catalog, cache manager, native extraction foundation.
- Full-access default covered: `GraalPyPythonRuntime` uses `allowAllAccess(true)` and `allowIO(IOAccess.ALL)`.
- UI responsiveness covered: `IdeExecutors`, EDT helper, background `ScriptExecutor`, non-blocking UI result publishing, bounded console buffer.
- IDE covered: Swing tab, editor, console, package panel, status bar, Run and Stop controls.
- Package support covered: GraalPy package list, catalog metadata, package smoke test task.
- Java-backed modules covered: encoder and crypto wrappers, HTTP bridge interface.
- Testing covered: unit tests, GraalPy smoke tests, package smoke tests, fat JAR build, manual Burp checklist.
- Release gaps explicitly remaining after this plan: Montoya proxy/repeater adapters, traceback line navigation, native-heavy package validation for numpy/pandas/cryptography/lxml, user-supplied package paths.

## References

- PortSwigger recommends Java/Montoya for new extensions: https://portswigger.net/burp/documentation/desktop/extend-burp/extensions/creating
- PortSwigger Gradle dependency format for Montoya: https://portswigger.net/burp/documentation/desktop/extend-burp/extensions/creating/set-up/manual-setup
- Montoya API Maven artifact: https://central.sonatype.com/artifact/net.portswigger.burp.extensions/montoya-api
- Montoya HTTP API Javadoc: https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/http/Http.html
- GraalPy embedding overview: https://www.graalvm.org/python/
- GraalPy build tools and VFS packaging: https://github.com/oracle/graalpython/blob/master/docs/user/Embedding-Build-Tools.md
- GraalPy embedding artifact: https://central.sonatype.com/artifact/org.graalvm.python/python-embedding
- JUnit Gradle BOM example: https://docs.junit.org/6.1.0/running-tests/build-support.html
- Bouncy Castle Java downloads: https://www.bouncycastle.org/download/bouncy-castle-java/
