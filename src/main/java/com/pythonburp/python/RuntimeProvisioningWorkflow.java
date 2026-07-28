package com.pythonburp.python;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class RuntimeProvisioningWorkflow {
    private final Path runtimeRoot;
    private final RuntimeProvisioningProbe probe;
    private final RuntimeProvisioningPrompt prompt;
    private final RuntimeProvisioner provisioner;

    public RuntimeProvisioningWorkflow(Path runtimeRoot,
                                       RuntimeProvisioningProbe probe,
                                       RuntimeProvisioningPrompt prompt,
                                       RuntimeProvisioner provisioner) {
        this.runtimeRoot = Objects.requireNonNull(runtimeRoot, "runtimeRoot").toAbsolutePath().normalize();
        this.probe = Objects.requireNonNull(probe, "probe");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.provisioner = Objects.requireNonNull(provisioner, "provisioner");
    }

    public static RuntimeProvisioningWorkflow fixedWindowsDefault() {
        Path runtimeRoot = NmapRuntimePaths.fixed().zenmapBin().resolve("BurpPythonIDE").normalize();
        return new RuntimeProvisioningWorkflow(
            runtimeRoot,
            RuntimeProvisioningProbe.fileSystem(),
            RuntimeProvisioningPrompt.swing(),
            new PowerShellRuntimeProvisioner());
    }

    public RuntimeProvisioningOutcome ensureReady() {
        try {
            probe.ensureWritable(runtimeRoot);
            return RuntimeProvisioningOutcome.success();
        } catch (IOException firstFailure) {
            if (!prompt.requestProvisioning(runtimeRoot, firstFailure)) {
                return RuntimeProvisioningOutcome.failure(
                    "Administrator permission was required to prepare "
                        + runtimeRoot
                        + ", but the request was declined.");
            }
            try {
                provisioner.provision(runtimeRoot);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return RuntimeProvisioningOutcome.failure(
                    "Interrupted while waiting for administrator provisioning of " + runtimeRoot + ".");
            } catch (IOException error) {
                return RuntimeProvisioningOutcome.failure(
                    "Failed to provision " + runtimeRoot + " with administrator permission: " + error.getMessage());
            }
            try {
                probe.ensureWritable(runtimeRoot);
                return RuntimeProvisioningOutcome.success();
            } catch (IOException retryFailure) {
                return RuntimeProvisioningOutcome.failure(
                    "Administrator provisioning completed, but "
                        + runtimeRoot
                        + " is still not writable: "
                        + retryFailure.getMessage());
            }
        }
    }
}
