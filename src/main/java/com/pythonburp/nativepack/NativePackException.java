package com.pythonburp.nativepack;

public final class NativePackException extends Exception {
    public NativePackException(String message) {
        super(message);
    }

    public NativePackException(String message, Throwable cause) {
        super(message, cause);
    }
}
