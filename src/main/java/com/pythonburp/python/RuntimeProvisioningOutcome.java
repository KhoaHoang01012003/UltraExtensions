package com.pythonburp.python;

public record RuntimeProvisioningOutcome(boolean ready, String message) {
    public static RuntimeProvisioningOutcome success() {
        return new RuntimeProvisioningOutcome(true, "");
    }

    public static RuntimeProvisioningOutcome failure(String message) {
        return new RuntimeProvisioningOutcome(false, message == null ? "" : message);
    }
}
