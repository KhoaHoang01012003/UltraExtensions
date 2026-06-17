package com.pythonburp.bridge;

public interface RepeaterBridge {
    void send(String method, String url, String body, String tabName);

    static RepeaterBridge unavailable() {
        return (method, url, body, tabName) -> {
            throw new IllegalStateException("Repeater bridge is not connected to Burp yet");
        };
    }
}
