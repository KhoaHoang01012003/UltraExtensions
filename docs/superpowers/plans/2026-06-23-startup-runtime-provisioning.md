# Startup Runtime Provisioning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a startup-time admin-elevation workflow so the extension can prepare the fixed Nmap runtime directory before creating runtime-backed services.

**Architecture:** Introduce a small provisioning workflow that probes writability, prompts for elevation, launches an elevated PowerShell helper, and retries once. Wire `BurpPythonIdeExtension` to stop startup cleanly when provisioning is declined or fails.

**Tech Stack:** Java 21+, Swing, PowerShell, JUnit 5, Gradle

---

### Task 1: Cover Startup Provisioning Workflow With Tests

**Files:**
- Create: `src/test/java/com/pythonburp/python/RuntimeProvisioningWorkflowTest.java`
- Modify: `src/test/java/com/pythonburp/BurpPythonIdeExtensionTest.java`

- [ ] Write failing tests for workflow success, prompt-decline, and provision-then-retry success.
- [ ] Run the targeted tests and verify they fail for missing workflow types or wiring.
- [ ] Add an extension initialization test that proves the suite tab is not registered when startup provisioning returns not-ready.

### Task 2: Implement Startup Provisioning Workflow

**Files:**
- Create: `src/main/java/com/pythonburp/python/RuntimeProvisioningWorkflow.java`
- Create: `src/main/java/com/pythonburp/python/RuntimeProvisioningPrompt.java`
- Create: `src/main/java/com/pythonburp/python/RuntimeProvisioner.java`
- Create: `src/main/java/com/pythonburp/python/RuntimeProvisioningProbe.java`
- Create: `src/main/java/com/pythonburp/python/PowerShellRuntimeProvisioner.java`

- [ ] Implement the smallest workflow that probes `...\BurpPythonIDE`, prompts on access failure, runs the elevated helper, and retries once.
- [ ] Keep the production default path fixed under the Nmap tree.
- [ ] Make the PowerShell helper create the target directory and grant Users modify access.

### Task 3: Wire Extension Startup Gating

**Files:**
- Modify: `src/main/java/com/pythonburp/BurpPythonIdeExtension.java`
- Modify: `src/test/java/com/pythonburp/BurpPythonIdeExtensionTest.java`

- [ ] Add constructor-level injection so startup provisioning can be stubbed in tests.
- [ ] Run the provisioning workflow before runtime/package/UI initialization.
- [ ] Log a clear startup error and exit early when runtime provisioning is declined or fails.

### Task 4: Verify And Rebuild

**Files:**
- Build output: `build/libs/burp-python-ide-enhanced-*-all.jar`
- Copy target: `extensions/`

- [ ] Run targeted provisioning and extension tests.
- [ ] Run `.\gradlew.bat check fatJar`.
- [ ] Copy the rebuilt `-all.jar` into `extensions/`.
